package com.localcut.editor.session

import com.localcut.editor.data.project.ProjectStore
import com.localcut.editor.domain.model.Project
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private var passed=0
private var failed=0
private fun test(name:String,block:()->Unit){try{block();passed++;println("PASS $name")}catch(t:Throwable){failed++;println("FAIL $name: ${t.message}")}}
private fun assertEquals(e:Any?,a:Any?,m:String=""){if(e!=a)error("expected=$e actual=$a $m")}
private fun assertTrue(v:Boolean,m:String="expected true"){if(!v)error(m)}

private class ManualScheduler: DelayScheduler {
    var action:(()->Unit)?=null
    override fun schedule(delayMs:Long, action:()->Unit):Cancellable {
        this.action=action
        return Cancellable { this.action=null }
    }
    fun fire(){ val a=action; action=null; a?.invoke() }
}

private class ConcurrentStore: ProjectStore {
    val firstEntered = CountDownLatch(1)
    val releaseFirst = CountDownLatch(1)
    private val active = AtomicInteger(0)
    val maxConcurrent = AtomicInteger(0)
    val savedTitles = Collections.synchronizedList(mutableListOf<String>())

    override fun save(project: Project): Result<Unit> {
        val now = active.incrementAndGet()
        maxConcurrent.updateAndGet { previous -> maxOf(previous, now) }
        try {
            if (project.title == "old") {
                firstEntered.countDown()
                check(releaseFirst.await(2, TimeUnit.SECONDS)) { "timed out waiting to release first save" }
            }
            savedTitles += project.title
            return Result.success(Unit)
        } finally {
            active.decrementAndGet()
        }
    }

    override fun load(): Result<Project> = Result.failure(IllegalStateException("unused"))
}

private class FlakyStore: ProjectStore {
    var saves=0
    var failNext=true
    var last:Project?=null
    override fun save(project:Project):Result<Unit>{
        saves++
        if(failNext){ failNext=false; return Result.failure(IllegalStateException("disk full")) }
        last=project
        return Result.success(Unit)
    }
    override fun load():Result<Project> = Result.failure(IllegalStateException("unused"))
}

fun main(){
    test("failed autosave retries automatically without another edit"){
        val scheduler=ManualScheduler(); val store=FlakyStore()
        val saver=DebouncedProjectSaver(store,scheduler,0)
        val p=Project.create("edited",1080,1920,30)
        saver.submit(p); scheduler.fire()
        assertTrue(saver.lastResult?.isFailure==true)
        assertTrue(scheduler.action != null, "failed autosave must schedule one automatic retry")
        scheduler.fire()
        assertTrue(saver.lastResult?.isSuccess==true)
        assertEquals(2,store.saves)
        assertEquals(p,store.last)
    }
    test("failed autosave keeps latest project pending for retry"){
        val scheduler=ManualScheduler(); val store=FlakyStore()
        val saver=DebouncedProjectSaver(store,scheduler,0)
        val p=Project.create("edited",1080,1920,30)
        saver.submit(p); scheduler.fire()
        assertTrue(saver.lastResult?.isFailure==true)
        val retry=saver.flushNow()
        assertTrue(retry.isSuccess)
        assertEquals(2,store.saves)
        assertEquals(p,store.last)
    }
    test("newer edit replaces retained failed autosave before retry"){
        val scheduler=ManualScheduler(); val store=FlakyStore()
        val saver=DebouncedProjectSaver(store,scheduler,0)
        val old=Project.create("old",1080,1920,30)
        val latest=Project.create("latest",1080,1920,30)
        saver.submit(old); scheduler.fire()
        saver.submit(latest); scheduler.fire()
        assertEquals(2,store.saves)
        assertEquals(latest,store.last)
    }
    test("scheduled save and flushNow never write project store concurrently"){
        val scheduler=ManualScheduler(); val store=ConcurrentStore()
        val saver=DebouncedProjectSaver(store,scheduler,0)
        val old=Project.create("old",1080,1920,30)
        val latest=Project.create("latest",1080,1920,30)
        saver.submit(old)
        val scheduledThread=Thread { scheduler.fire() }
        scheduledThread.start()
        assertTrue(store.firstEntered.await(1, TimeUnit.SECONDS), "first save did not start")

        saver.submit(latest)
        val flushThread=Thread { saver.flushNow() }
        flushThread.start()
        Thread.sleep(100)
        store.releaseFirst.countDown()
        scheduledThread.join(2_000)
        flushThread.join(2_000)

        assertEquals(1, store.maxConcurrent.get(), "project store saves overlapped")
        assertEquals("latest", store.savedTitles.lastOrNull(), "latest project must win")
    }
    println("RESULT passed=$passed failed=$failed")
    if(failed!=0)error("$failed tests failed")
}

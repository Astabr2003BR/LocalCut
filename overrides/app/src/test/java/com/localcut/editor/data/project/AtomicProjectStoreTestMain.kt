package com.localcut.editor.data.project

import com.localcut.editor.BuildContract
import com.localcut.editor.domain.model.*
import com.localcut.editor.domain.timeline.TimelineOps
import java.nio.file.Files

private var passed=0
private var failed=0
private fun test(name:String,block:()->Unit){try{block();passed++;println("PASS $name")}catch(t:Throwable){failed++;println("FAIL $name: ${t.message}")}}
private fun assertEquals(e:Any?,a:Any?,m:String=""){if(e!=a)error("expected=$e actual=$a $m")}
private fun assertTrue(v:Boolean,m:String="expected true"){if(!v)error(m)}

private fun project(title:String="Projeto çã 日本", startUs:Long=0): Project {
    val video=Asset("v",AssetType.VIDEO,"content://media/external/video/42",12_000_000,987654,"vídeo ção 日本.mp4","video/mp4","size:987654:last:7")
    val audio=Asset("a",AssetType.AUDIO,"content://media/external/audio/9",15_000_000,12345,"áudio música.m4a","audio/mp4","size:12345:last:9")
    var p=Project.create(title,1080,1920,30,"p-1").copy(assets=listOf(video,audio))
    val vt=p.tracks.first{it.type==TrackType.VISUAL}
    val at=p.tracks.first{it.type==TrackType.AUDIO}
    p=TimelineOps.insertClip(p,vt.id,Clip("vc","v",startUs,5_000_000,1_000_000,6_000_000,transform=TransformValue(x=1.5,y=-2.0,scale=1.1,rotationDeg=12.5,opacity=.9),opacity=.8,keyframes=listOf(TransformKeyframe(0,TransformValue(scale=1.0)),TransformKeyframe(5_000_000,TransformValue(scale=1.2)))))
    p=TimelineOps.insertClip(p,at.id,Clip("ac","a",0,7_000_000,2_000_000,9_000_000,volume=.6))
    return p
}

fun main(){
    test("project JSON round trip preserves exact model and unicode"){
        val p=project()
        val json=ProjectJson.encode(p)
        assertTrue(json.contains("Projeto çã 日本"))
        assertEquals(p,ProjectJson.decode(json))
    }
    test("unknown root fields are tolerated"){
        val p=project()
        val json=ProjectJson.encode(p).replaceFirst("{","{\"futureField\":{\"nested\":[1,true,null]},")
        assertEquals(p,ProjectJson.decode(json))
    }
    test("partial temp file is ignored on load"){
        val dir=Files.createTempDirectory("localcut-store-")
        try{
            val store=AtomicProjectStore(dir)
            assertTrue(store.save(project()).isSuccess)
            Files.writeString(dir.resolve("project.json.tmp"),"{broken")
            assertEquals(project(),store.load().getOrThrow())
        }finally{dir.toFile().deleteRecursively()}
    }
    test("valid temp file recovers interrupted first save"){
        val dir=Files.createTempDirectory("localcut-store-")
        try{
            val expected=project("interrupted-first-save")
            Files.writeString(dir.resolve("project.json.tmp"),ProjectJson.encode(expected))
            val loaded=AtomicProjectStore(dir).load().getOrThrow()
            assertEquals(expected,loaded)
            assertTrue(Files.exists(dir.resolve("project.json")),"valid temp recovery must promote project.json")
        }finally{dir.toFile().deleteRecursively()}
    }
    test("corrupted current file falls back to last known good backup"){
        val dir=Files.createTempDirectory("localcut-store-")
        try{
            val store=AtomicProjectStore(dir)
            val p1=project("one",0); val p2=project("two",1_000_000)
            assertTrue(store.save(p1).isSuccess); assertTrue(store.save(p2).isSuccess)
            Files.writeString(dir.resolve("project.json"),"not json")
            assertEquals(p1,store.load().getOrThrow())
        }finally{dir.toFile().deleteRecursively()}
    }
    test("newer unsupported schema is reported instead of falling back"){
        val dir=Files.createTempDirectory("localcut-store-")
        try{
            val store=AtomicProjectStore(dir)
            assertTrue(store.save(project("backup")).isSuccess)
            assertTrue(store.save(project("current")).isSuccess)
            val currentSchema="\"schemaVersion\":${BuildContract.PROJECT_SCHEMA_VERSION}"
            val newer=ProjectJson.encode(project("newer")).replace(currentSchema,"\"schemaVersion\":999")
            Files.writeString(dir.resolve("project.json"),newer)
            val result=store.load()
            assertTrue(result.isFailure)
            val e=result.exceptionOrNull() as ProjectStoreException
            assertEquals(ProjectStoreErrorCode.UNSUPPORTED_SCHEMA,e.code)
        }finally{dir.toFile().deleteRecursively()}
    }
    test("save and recovery never mutate asset references"){
        val dir=Files.createTempDirectory("localcut-store-")
        try{
            val p=project(); val before=p.assets
            val store=AtomicProjectStore(dir)
            store.save(p).getOrThrow()
            assertEquals(before,p.assets)
            assertEquals(before,store.load().getOrThrow().assets)
        }finally{dir.toFile().deleteRecursively()}
    }
    println("RESULT passed=$passed failed=$failed")
    if(failed!=0)error("$failed tests failed")
}

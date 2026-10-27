package com.example.pokemonalertsv2.catchroutes
import org.junit.Assert.*
import org.junit.Test
class CatchRouteDrawingTest {
 private fun p(x:Double,y:Double)=CatchPoint(49.74+y/111195,8.62+x/(111195*kotlin.math.cos(Math.toRadians(49.74))))
 private fun path(vararg points:CatchPoint):List<CatchPathPosition>{var m=0.0;return points.mapIndexed{i,v->if(i>0)m+=catchDistance(points[i-1],v);CatchPathPosition(v,m)}}
 @Test fun outAndBackIsOneLineWithoutLanes(){
  val original=path(p(0.0,0.0),p(400.0,0.0),p(0.0,0.0));val before=original.toList();val d=catchRouteDrawing(original)
  assertEquals(before,original)
  assertEquals(listOf("Start / Finish"),d.endpoints.map{it.label})
  assertTrue("no numbered stops without catches",d.stops.isEmpty())
  // The preview is all "next": the whole plan, on the street itself.
  val preview=catchRouteSlices(original,null,null)
  assertTrue(preview.walked.isEmpty());assertTrue(preview.later.isEmpty())
  assertEquals(0.0,catchDistance(preview.next.first(),p(0.0,0.0)),0.5)
  assertEquals(0.0,catchDistance(preview.next[1],p(400.0,0.0)),0.5)
 }
 @Test fun stopsAreNumberedInWalkingOrderAtTheirPositions(){
  val d=catchRouteDrawing(path(p(0.0,0.0),p(400.0,0.0),p(0.0,0.0)),listOf(600.0,150.0,390.0))
  assertEquals(listOf("1","2","3"),d.stops.map{it.label})
  assertEquals(listOf(150.0,390.0,600.0),d.stops.map{it.meters})
  // 600 m is on the way back, 200 m from the start.
  assertEquals(200.0,catchDistance(d.stops[2].point,p(0.0,0.0)),1.0)
 }
 @Test fun slicesSplitAtProgressAndNextStop(){
  val route=path(p(0.0,0.0),p(400.0,0.0),p(400.0,400.0))
  val s=catchRouteSlices(route,100.0,500.0)
  assertEquals(100.0,catchDistance(s.walked.first(),s.walked.last()),1.0)
  assertEquals(0.0,catchDistance(s.next.first(),p(100.0,0.0)),0.5)
  assertEquals(0.0,catchDistance(s.next.last(),p(400.0,100.0)),0.5)
  assertTrue("the corner is kept",s.next.any{catchDistance(it,p(400.0,0.0))<0.5})
  assertEquals(0.0,catchDistance(s.later.first(),p(400.0,100.0)),0.5)
  assertEquals(0.0,catchDistance(s.later.last(),p(400.0,400.0)),0.5)
 }
 @Test fun noStopAheadRunsTheNextLegToTheFinish(){
  val route=path(p(0.0,0.0),p(400.0,0.0))
  val s=catchRouteSlices(route,150.0,null)
  assertTrue(s.later.isEmpty())
  assertEquals(0.0,catchDistance(s.next.last(),p(400.0,0.0)),0.5)
 }
 @Test fun progressPastTheEndLeavesOnlyTheWalkedLine(){
  val route=path(p(0.0,0.0),p(400.0,0.0))
  val s=catchRouteSlices(route,900.0,950.0)
  assertTrue(s.next.isEmpty());assertTrue(s.later.isEmpty())
  assertEquals(0.0,catchDistance(s.walked.last(),p(400.0,0.0)),0.5)
 }
 @Test fun openRouteAndStationaryRouteHaveClearEndpoints(){
  assertEquals(listOf("Start","Finish"),catchRouteDrawing(path(p(0.0,0.0),p(100.0,0.0))).endpoints.map{it.label})
  assertTrue(catchRouteDrawing(emptyList()).endpoints.isEmpty())
  assertEquals("Start / Finish",catchRouteDrawing(path(p(0.0,0.0))).endpoints.single().label)
  assertTrue(catchRouteSlices(path(p(0.0,0.0)),0.0,null).next.isEmpty())
 }
}

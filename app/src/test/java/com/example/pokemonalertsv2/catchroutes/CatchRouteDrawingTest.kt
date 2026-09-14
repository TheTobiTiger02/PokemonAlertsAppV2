package com.example.pokemonalertsv2.catchroutes
import org.junit.Assert.*
import org.junit.Test
class CatchRouteDrawingTest {
 private fun p(x:Double,y:Double)=CatchPoint(49.74+y/111195,8.62+x/(111195*kotlin.math.cos(Math.toRadians(49.74))))
 private fun path(vararg points:CatchPoint):List<CatchPathPosition>{var m=0.0;return points.mapIndexed{i,v->if(i>0)m+=catchDistance(points[i-1],v);CatchPathPosition(v,m)}}
 @Test fun repeatedRoadHasSeparateLanesAndOppositeArrows(){
  val original=path(p(0.0,0.0),p(400.0,0.0),p(0.0,0.0));val before=original.toList();val d=catchRouteDrawing(original)
  assertEquals(before,original);assertEquals(3,d.segments.size)
  assertTrue(catchDistance(d.segments[0].from,d.segments.last().to)>7)
  assertTrue(d.arrows.any{it.bearing>80} && d.arrows.any{it.bearing< -80})
  assertEquals("Start / Finish",d.stages.first().label);assertEquals(listOf("1","2","3","4"),d.stages.map{it.label}.filter{it.toIntOrNull()!=null})
 }
 @Test fun figureEightKeepsTraversalOrderAndFiniteGeometry(){
  val d=catchRouteDrawing(path(p(0.0,0.0),p(200.0,200.0),p(-200.0,200.0),p(200.0,-200.0),p(-200.0,-200.0),p(0.0,0.0)))
  assertTrue(d.segments.size>=5);assertTrue(d.arrows.size>10);assertTrue(d.segments.all{it.from.valid&&it.to.valid});assertEquals("Start / Finish",d.stages[0].label)
 }
 @Test fun differentVertexSegmentationStillSeparatesReturnPath(){
  val d=catchRouteDrawing(path(p(0.0,0.0),p(400.0,0.0),p(200.0,0.0),p(0.0,0.0)))
  assertTrue(catchDistance(d.segments.first().from,d.segments.last().to)>7)
  assertTrue(d.stages.any{it.label=="Turn back"})
 }
 @Test fun openRouteAndStationaryRouteHaveClearEndpoints(){
  assertEquals(listOf("Start","Finish"),catchRouteDrawing(path(p(0.0,0.0),p(100.0,0.0))).stages.map{it.label})
  assertTrue(catchRouteDrawing(emptyList()).segments.isEmpty())
  assertEquals("Start / Finish",catchRouteDrawing(path(p(0.0,0.0))).stages.single().label)
 }
}

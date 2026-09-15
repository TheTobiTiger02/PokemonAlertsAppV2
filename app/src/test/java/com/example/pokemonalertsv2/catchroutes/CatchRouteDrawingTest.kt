package com.example.pokemonalertsv2.catchroutes
import org.junit.Assert.*
import org.junit.Test
class CatchRouteDrawingTest {
 private fun p(x:Double,y:Double)=CatchPoint(49.74+y/111195,8.62+x/(111195*kotlin.math.cos(Math.toRadians(49.74))))
 private fun path(vararg points:CatchPoint):List<CatchPathPosition>{var m=0.0;return points.mapIndexed{i,v->if(i>0)m+=catchDistance(points[i-1],v);CatchPathPosition(v,m)}}
 @Test fun repeatedRoadHasSeparateLanesAndOppositeArrows(){
  val original=path(p(0.0,0.0),p(400.0,0.0),p(0.0,0.0));val before=original.toList();val d=catchRouteDrawing(original)
  assertEquals(before,original);assertEquals(3,d.segments.size)
  assertTrue(catchDistance(d.segments[0].from,d.segments.last().to)>5)
  assertTrue(d.arrows.any{it.bearing>80} && d.arrows.any{it.bearing< -80})
  assertEquals(listOf("Start / Finish"),d.endpoints.map{it.label})
  assertTrue("no numbered stops without catches",d.stops.isEmpty())
 }
 @Test fun stopsAreNumberedInWalkingOrderAtTheirPositions(){
  val d=catchRouteDrawing(path(p(0.0,0.0),p(400.0,0.0),p(0.0,0.0)),listOf(600.0,150.0,390.0))
  assertEquals(listOf("1","2","3"),d.stops.map{it.label})
  assertEquals(listOf(150.0,390.0,600.0),d.stops.map{it.meters})
  // 600 m is on the way back, 200 m from the start, in the return lane.
  assertEquals(200.0,catchDistance(d.stops[2].point,p(0.0,0.0)),8.0)
 }
 @Test fun figureEightKeepsTraversalOrderAndFiniteGeometry(){
  val d=catchRouteDrawing(path(p(0.0,0.0),p(200.0,200.0),p(-200.0,200.0),p(200.0,-200.0),p(-200.0,-200.0),p(0.0,0.0)))
  assertTrue(d.segments.size>=5);assertTrue(d.arrows.size>10);assertTrue(d.segments.all{it.from.valid&&it.to.valid})
  assertTrue(d.segments.zipWithNext().all{(a,b)->b.startMeters>=a.startMeters})
 }
 @Test fun openRouteAndStationaryRouteHaveClearEndpoints(){
  assertEquals(listOf("Start","Finish"),catchRouteDrawing(path(p(0.0,0.0),p(100.0,0.0))).endpoints.map{it.label})
  assertTrue(catchRouteDrawing(emptyList()).segments.isEmpty())
  assertEquals("Start / Finish",catchRouteDrawing(path(p(0.0,0.0))).endpoints.single().label)
 }
 @Test fun routeColourRunsFromBlueToViolet(){
  assertEquals(0xFF1E63F0.toInt(),catchRouteColor(0.0));assertEquals(0xFF8B3FE0.toInt(),catchRouteColor(1.0));assertEquals(catchRouteColor(1.0),catchRouteColor(4.0))
 }
}

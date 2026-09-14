package com.example.pokemonalertsv2.catchroutes
import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class CatchRouteDrawingInstrumentedTest {
 @Test fun crossingAndRepeatedRoadPreviews(){
  val context=ApplicationProvider.getApplicationContext<Context>();val instrumentation=InstrumentationRegistry.getInstrumentation()
  fun p(x:Double,y:Double)=CatchPoint(49.8728+y/111195,8.6512+x/(111195*kotlin.math.cos(Math.toRadians(49.8728))))
  val shapes=mapOf("figure-eight" to listOf(p(0.0,0.0),p(200.0,200.0),p(-200.0,200.0),p(200.0,-200.0),p(-200.0,-200.0),p(0.0,0.0)),"out-and-back" to listOf(p(0.0,0.0),p(0.0,400.0),p(0.0,0.0)))
  for((name,points)in shapes)ActivityScenario.launch(CatchRoutesActivity::class.java).use { scenario ->
   var ready=false;var failed=false;var view:CatchRouteMapView?=null
   scenario.onActivity{activity->
    var meters=0.0;val path=points.mapIndexed{i,v->if(i>0)meters+=catchDistance(points[i-1],v);CatchPathPosition(v,meters)}
    val settings=CatchRouteSettings(start=points.first(),startAtMillis=System.currentTimeMillis())
    view=CatchRouteMapView(activity).apply{onReady={ready=true};onFailure={failed=true};update(settings,CatchItinerary(settings,path,emptyList(),points),null)}
    activity.setContentView(view,FrameLayout.LayoutParams(-1,-1))
   }
   val until=System.currentTimeMillis()+15000
   while(!ready&&!failed&&System.currentTimeMillis()<until){Thread.sleep(100);instrumentation.waitForIdleSync()}
   assertTrue("Map did not load",ready&&!failed)
   scenario.onActivity { view!!.fit() }
   Thread.sleep(4000)
   val screenshot=instrumentation.uiAutomation.takeScreenshot()
   File(context.getExternalFilesDir(null),"catch-drawing-$name.png").outputStream().use{screenshot.compress(Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
   scenario.onActivity{view?.destroy()}
  }
 }
}

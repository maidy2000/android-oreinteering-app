//package tw.org.iii.www.stopwatch
//
//import android.R
//import android.os.Bundle
//import android.os.Handler
//import android.os.Message
//import android.view.View
//import android.widget.Button
//import android.widget.ListView
//import android.widget.SimpleAdapter
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import java.util.*
//
//class MainActivity : AppCompatActivity() {
//    private var clock: TextView? = null
//    private var btnLeft: Button? = null
//    private var btnRight: Button? = null
//    private var isRunning = false
//    private var counter = 0
//    private var timer: Timer? = null
//    private var handler: UIHandler? = null
//    private var countTask: CountTask? = null
//    private var lapList: ListView? = null
//    private var adapter: SimpleAdapter? = null
//    private val from = arrayOf("title")
//    private val to = intArrayOf(R.id.lapitem_title)
//    private var data: LinkedList<HashMap<String, String?>>? = null
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//        clock = findViewById<View>(R.id.clock) as TextView
//        btnLeft = findViewById<View>(R.id.btnLeft) as Button
//        btnRight = findViewById<View>(R.id.btnRight) as Button
//        lapList = findViewById<View>(R.id.lapList) as ListView
//        initListView()
//        timer = Timer()
//        handler = UIHandler()
//    }
//
//    override fun finish() {
//        timer!!.purge()
//        timer!!.cancel()
//        timer = null
//        super.finish()
//    }
//
//    private fun initListView() {
//        data = LinkedList()
//        adapter = SimpleAdapter(
//            this, data, R.layout.layout_lapitem, from, to
//        )
//        lapList!!.adapter = adapter
//    }
//
//    // Reset / Lap
//    fun doLeft(v: View?) {
//        if (isRunning) {
//            doLap()
//        } else {
//            doReset()
//        }
//    }
//
//    // Start / Stop
//    fun doRight(v: View?) {
//        isRunning = !isRunning
//        btnRight!!.text = if (isRunning) "Stop" else "Start"
//        btnLeft!!.text = if (isRunning) "Lap" else "Reset"
//        if (isRunning) {
//            doStart()
//        } else {
//            doStop()
//        }
//    }
//
//    private fun doStart() {
//        countTask = CountTask()
//        timer!!.schedule(countTask, 0, 10)
//    }
//
//    private fun doStop() {
//        if (countTask != null) {
//            countTask!!.cancel()
//            countTask = null
//        }
//    }
//
//    private fun doLap() {
//        val lap = HashMap<String, String?>()
//        lap[from[0]] = "" + counter
//        data!!.add(0, lap)
//        adapter!!.notifyDataSetChanged()
//    }
//
//    private fun doReset() {
//        counter = 0
//        handler!!.sendEmptyMessage(0)
//        data!!.clear()
//        adapter!!.notifyDataSetChanged()
//    }
//
//    private inner class CountTask : TimerTask() {
//        override fun run() {
//            counter++
//            handler!!.sendEmptyMessage(0)
//        }
//    }
//
//    private inner class UIHandler : Handler() {
//        override fun handleMessage(msg: Message) {
//            super.handleMessage(msg)
//            clock!!.text = "" + counter
//        }
//    }
//}
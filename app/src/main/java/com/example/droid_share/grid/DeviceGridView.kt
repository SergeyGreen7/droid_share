package com.example.droid_share.grid

import android.content.Context
import android.net.http.UrlRequest.Status
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.droid_share.StatusUpdater

class DeviceGridView(
    context : Context,
    private val recyclerView: RecyclerView,
    private var statusUpdater : StatusUpdater,
    private val numColumns: Int
) {
    var customAdapter: DeviceCustomAdapter
    val gridUpdater: GridUpdater

    init {
        recyclerView.layoutManager = GridLayoutManager(context,numColumns)
        customAdapter = DeviceCustomAdapter(statusUpdater)
        recyclerView.adapter = customAdapter

        val tmpList = listOf(
            DeviceInfo("qeqwe","qweqwe"),
            DeviceInfo("asdasdasd","asdasdasd")
        )
        customAdapter.updateDataSet(tmpList)

        gridUpdater = object: GridUpdater{
            override fun onDeviceListUpdate(deviceList: List<DeviceInfo>) {
                customAdapter.updateDataSet(deviceList)
            }
        }
    }
}

interface GridUpdater {
    fun onDeviceListUpdate(deviceList: List<DeviceInfo>)
}

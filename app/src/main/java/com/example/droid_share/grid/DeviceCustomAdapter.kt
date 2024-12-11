package com.example.droid_share.grid

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Color
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.droid_share.StatusUpdater
import com.example.droid_share.R

class DeviceCustomAdapter (
    private var statusUpdater : StatusUpdater,
    )
    : RecyclerView.Adapter<DeviceCustomAdapter.DeviceViewHolder>()
{

    private var selectedPos = RecyclerView.NO_POSITION
    private var dataSet = mutableListOf<DeviceInfo>()

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.device_name_vd)
        val deviceInfo: TextView = view.findViewById(R.id.device_details_vd)
        val icon: ImageView = view.findViewById(R.id.icon_vd)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun getItemCount(): Int {
        return dataSet.size
    }

    @SuppressLint("RecyclerView", "NotifyDataSetChanged", "ResourceAsColor",
        "UseCompatLoadingForDrawables"
    )
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        if(selectedPos == position)
             holder.itemView.setBackgroundResource(R.color.item_selection_color);
        else
            holder.itemView.setBackgroundColor(0x00FFFFFF);

        holder.itemView.setOnClickListener {
            selectedPos = position
            onDataChanged()
        }

        val info = dataSet[position]
        when (info.type) {
            InfoType.TEST -> {
                holder.icon.setImageResource(R.drawable.machine);
            }
            InfoType.WIFI_DIRECT_PEER -> {
                holder.icon.setImageResource(R.drawable.wifi_direct_peer_machine)
            }
            InfoType.WIFI_DIRECT_SERVICE -> {
                holder.icon.setImageResource(R.drawable.wifi_direct_service_machine)
            }
            InfoType.BLUETOOTH -> {
                holder.icon.setImageResource(R.drawable.bluetooth_machine)
            }
            InfoType.NSD -> {
                holder.icon.setImageResource(R.drawable.nsd_machine)
            }
            InfoType.BLE -> {
                holder.icon.setImageResource(R.drawable.ble_machine)
            }
        }
        holder.deviceName.text = buildString {
            append(info.type.toString())
            append(": ")
            append(info.deviceName)
        }
        holder.deviceInfo.text = info.deviceInfo
    }

    fun updateDataSet(newDataSet: List<DeviceInfo>) {
        dataSet = newDataSet.toMutableList()
        onDataChanged()
    }

    fun getSelectedData() : DeviceInfo? {
        if (selectedPos != RecyclerView.NO_POSITION) {
            return dataSet[selectedPos]
        }
        return null
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onDataChanged() {
        onItemSelection()
        notifyDataSetChanged()
    }

    private fun onItemSelection() {
        if (selectedPos != RecyclerView.NO_POSITION && dataSet.size > selectedPos) {
            statusUpdater.onDeviceInfoUpdate(dataSet[selectedPos])
        } else {
            statusUpdater.onDeviceInfoUpdate(null)
        }
    }
}

package com.wildtribe.drive.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wildtribe.drive.R
import com.wildtribe.drive.model.BleDevice

/**
 * RecyclerView adapter for the BLE device scan list.
 *
 * MotoRound devices are visually highlighted and sorted to the top.
 */
class DeviceAdapter(
    private val onConnectClick: (BleDevice) -> Unit
) : ListAdapter<BleDevice, DeviceAdapter.DeviceViewHolder>(DIFF_CALLBACK) {

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvDeviceRssi)
        private val tvSignal: TextView = itemView.findViewById(R.id.tvSignalStrength)
        private val btnConnect: Button = itemView.findViewById(R.id.btnConnect)
        private val motoroundBadge: View = itemView.findViewById(R.id.motoroundBadge)

        fun bind(device: BleDevice) {
            tvName.text = if (device.name.isBlank()) "Unknown Device" else device.name
            tvAddress.text = device.address
            tvRssi.text = "${device.rssi} dBm"
            tvSignal.text = device.signalStrength

            // Highlight MotoRound devices
            if (device.isMotoRound) {
                motoroundBadge.visibility = View.VISIBLE
                itemView.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.motoround_highlight)
                )
                tvName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.orange_primary)
                )
            } else {
                motoroundBadge.visibility = View.GONE
                itemView.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.surface_card)
                )
                tvName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_primary)
                )
            }

            // Signal strength color
            tvRssi.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    when {
                        device.rssi >= -60 -> R.color.signal_excellent
                        device.rssi >= -70 -> R.color.signal_good
                        device.rssi >= -80 -> R.color.signal_fair
                        else -> R.color.signal_weak
                    }
                )
            )

            btnConnect.setOnClickListener { onConnectClick(device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BleDevice>() {
            override fun areItemsTheSame(old: BleDevice, new: BleDevice) =
                old.address == new.address

            override fun areContentsTheSame(old: BleDevice, new: BleDevice) =
                old == new
        }
    }
}

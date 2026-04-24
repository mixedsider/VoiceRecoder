package com.voicelog.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voicelog.databinding.ItemDateHeaderBinding
import com.voicelog.databinding.ItemRecordingBinding
import com.voicelog.databinding.ItemSummaryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter : ListAdapter<RecordingUiItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
        private const val TYPE_SUMMARY = 2
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.KOREA)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is RecordingUiItem.Header -> TYPE_HEADER
        is RecordingUiItem.RecordingItem -> TYPE_ITEM
        is RecordingUiItem.SummaryItem -> TYPE_SUMMARY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemDateHeaderBinding.inflate(inflater, parent, false))
            TYPE_SUMMARY -> SummaryViewHolder(ItemSummaryBinding.inflate(inflater, parent, false))
            else -> ItemViewHolder(ItemRecordingBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RecordingUiItem.Header -> (holder as HeaderViewHolder).bind(item)
            is RecordingUiItem.RecordingItem -> (holder as ItemViewHolder).bind(item)
            is RecordingUiItem.SummaryItem -> (holder as SummaryViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecordingUiItem.Header) {
            binding.tvDateHeader.text = item.dateLabel
        }
    }

    class SummaryViewHolder(private val binding: ItemSummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecordingUiItem.SummaryItem) {
            binding.tvSummaryText.text = item.summary.summaryText
        }
    }

    class ItemViewHolder(private val binding: ItemRecordingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecordingUiItem.RecordingItem) {
            val rec = item.recording
            val start = timeFmt.format(Date(rec.startedAt))
            val end = timeFmt.format(Date(rec.startedAt + rec.durationSec * 1000L))
            val durationText = if (rec.durationSec < 60) "${rec.durationSec}초" else "${rec.durationSec / 60}분"
            binding.tvTimeRange.text = "$start ~ $end  [$durationText]"

            when {
                item.transcript != null -> {
                    binding.tvSummary.text = item.transcript.text
                    binding.tvStatus.visibility = View.GONE
                }
                rec.status == "pending" || rec.status == "transcribing" || rec.status == "summarizing" -> {
                    binding.tvSummary.text = ""
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "처리 중... ⏳"
                }
                else -> {
                    binding.tvSummary.text = "(내용 없음)"
                    binding.tvStatus.visibility = View.GONE
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RecordingUiItem>() {
        override fun areItemsTheSame(oldItem: RecordingUiItem, newItem: RecordingUiItem): Boolean {
            return when {
                oldItem is RecordingUiItem.Header && newItem is RecordingUiItem.Header ->
                    oldItem.dateLabel == newItem.dateLabel
                oldItem is RecordingUiItem.RecordingItem && newItem is RecordingUiItem.RecordingItem ->
                    oldItem.recording.id == newItem.recording.id
                oldItem is RecordingUiItem.SummaryItem && newItem is RecordingUiItem.SummaryItem ->
                    oldItem.summary.date == newItem.summary.date
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: RecordingUiItem, newItem: RecordingUiItem) =
            oldItem == newItem
    }
}

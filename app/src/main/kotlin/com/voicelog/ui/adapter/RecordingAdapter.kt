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
import com.voicelog.util.SummaryTextFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter(
    private val onRecordingClick: (RecordingUiItem.RecordingItem) -> Unit = {},
    private val onSummaryClick: (RecordingUiItem.SummaryItem) -> Unit = {},
) : ListAdapter<RecordingUiItem, RecyclerView.ViewHolder>(DiffCallback()) {

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
            TYPE_SUMMARY -> SummaryViewHolder(
                ItemSummaryBinding.inflate(inflater, parent, false),
                onSummaryClick
            )
            else -> ItemViewHolder(
                ItemRecordingBinding.inflate(inflater, parent, false),
                onRecordingClick
            )
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

    class SummaryViewHolder(
        private val binding: ItemSummaryBinding,
        private val onClick: (RecordingUiItem.SummaryItem) -> Unit,
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecordingUiItem.SummaryItem) {
            binding.tvSummaryText.text = SummaryTextFormatter.normalize(item.summary.summaryText)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    class ItemViewHolder(
        private val binding: ItemRecordingBinding,
        private val onClick: (RecordingUiItem.RecordingItem) -> Unit,
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecordingUiItem.RecordingItem) {
            val rec = item.recording
            val start = timeFmt.format(Date(rec.startedAt))
            val end = timeFmt.format(Date(rec.startedAt + rec.durationSec * 1000L))
            val durationText = if (rec.durationSec < 60) {
                "${rec.durationSec}s"
            } else {
                "${rec.durationSec / 60}m"
            }
            binding.tvTimeRange.text = "$start ~ $end  [$durationText]"

            when {
                item.transcript != null -> {
                    binding.tvSummary.text = item.transcript.text
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = when (rec.status) {
                        "transcript_ready" -> "STT 완료, 요약 대기중"
                        "pending", "queued" -> "처리 대기중"
                        "summarizing" -> "STT 완료, 요약 생성중"
                        "done" -> "STT 완료"
                        else -> "STT 완료"
                    }
                }

                rec.status == "pending" -> {
                    binding.tvSummary.text = ""
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "STT 대기중"
                }

                rec.status == "queued" -> {
                    binding.tvSummary.text = ""
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "STT 처리 대기중"
                }

                rec.status == "transcribing" -> {
                    binding.tvSummary.text = ""
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "STT 처리중"
                }

                rec.status == "transcript_ready" -> {
                    binding.tvSummary.text = ""
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "요약 대기중"
                }

                rec.status == "summarizing" -> {
                    binding.tvSummary.text = ""
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "요약 생성중"
                }

                rec.status == "failed" -> {
                    binding.tvSummary.text = ""
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "처리 실패"
                }

                else -> {
                    binding.tvSummary.text = "(No content)"
                    binding.tvStatus.visibility = View.GONE
                }
            }

            binding.root.setOnClickListener { onClick(item) }
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

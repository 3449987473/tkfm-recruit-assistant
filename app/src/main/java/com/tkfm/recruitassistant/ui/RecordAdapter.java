package com.tkfm.recruitassistant.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tkfm.recruitassistant.R;
import com.tkfm.recruitassistant.core.HistoryStore;
import com.tkfm.recruitassistant.util.Ui;

import java.util.ArrayList;
import java.util.List;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.Holder> {

    public interface OnRecordClick {
        void onRecordClick(HistoryStore.Record record);
    }

    private final List<HistoryStore.Record> items = new ArrayList<>();
    private final OnRecordClick listener;

    public RecordAdapter(OnRecordClick listener) {
        this.listener = listener;
    }

    public void submit(List<HistoryStore.Record> records) {
        items.clear();
        items.addAll(records);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HistoryStore.Record record = items.get(position);
        holder.time.setText(Ui.formatTime(record.time));
        holder.tags.setText(record.tags.isEmpty() ? "（无词条）" : String.join(" · ", record.tags));
        holder.best.setText(record.bestCombo.isEmpty() ? "无匹配结果"
                : "最佳组合：" + record.bestCombo + "  →  " + record.summary());
        holder.count.setText(record.groupCount + " 组组合 · " + record.characterCount + " 个候选条目");
        if (!record.guarantee.isEmpty()) {
            holder.star.setText(record.guarantee);
            holder.star.setTextColor(record.guarantee.startsWith("必出") ? Ui.RED : Ui.GOLD);
            holder.star.setVisibility(View.VISIBLE);
        } else if (record.bestStar > 0) {
            // Records written before 1.1.4 only carry the highest star.
            holder.star.setText("最高 " + record.bestStar + "★");
            holder.star.setTextColor(Ui.starColor(record.bestStar));
            holder.star.setVisibility(View.VISIBLE);
        } else {
            holder.star.setVisibility(View.GONE);
        }
        boolean auto = "自动".equals(record.mode);
        holder.mode.setText(auto ? "自动识别" : "手动计算");
        holder.mode.setTextColor(auto ? Ui.ACCENT : Ui.PRIMARY);
        holder.mode.setBackground(Ui.rounded(
                auto ? Ui.withAlpha(Ui.ACCENT, 0x1E) : Ui.withAlpha(Ui.PRIMARY, 0x1E),
                auto ? Ui.withAlpha(Ui.ACCENT, 0x55) : Ui.withAlpha(Ui.PRIMARY, 0x55), 7, 1,
                holder.itemView.getContext()));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRecordClick(record);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView mode;
        final TextView time;
        final TextView star;
        final TextView tags;
        final TextView best;
        final TextView count;

        Holder(@NonNull View itemView) {
            super(itemView);
            mode = itemView.findViewById(R.id.modeBadge);
            time = itemView.findViewById(R.id.timeText);
            star = itemView.findViewById(R.id.starText);
            tags = itemView.findViewById(R.id.tagsText);
            best = itemView.findViewById(R.id.bestText);
            count = itemView.findViewById(R.id.countText);
        }
    }
}

package com.tkfm.recruitassistant.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tkfm.recruitassistant.MainActivity;
import com.tkfm.recruitassistant.R;
import com.tkfm.recruitassistant.core.HistoryStore;

import java.util.List;

public class RecordsFragment extends Fragment {

    private HistoryStore history;
    private RecordAdapter adapter;
    private TextView empty;
    private TextView count;
    private RecyclerView list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_records, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        history = new HistoryStore(requireContext());
        empty = view.findViewById(R.id.emptyText);
        count = view.findViewById(R.id.recordCount);
        list = view.findViewById(R.id.recyclerView);
        adapter = new RecordAdapter(record -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).loadManualTags(record.tags);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        view.findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());
        refresh();
    }

    public void refresh() {
        if (history == null) return;
        List<HistoryStore.Record> records = history.list();
        adapter.submit(records);
        count.setText(records.size() + " 条分析记录");
        empty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(records.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmClear() {
        if (history.list().isEmpty()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("清空招募记录？")
                .setMessage("所有本机分析记录将被删除，无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    history.clear();
                    refresh();
                    UiHelper.toast(requireContext(), "记录已清空");
                })
                .show();
    }

    private static final class UiHelper {
        static void toast(android.content.Context context, String text) {
            android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}

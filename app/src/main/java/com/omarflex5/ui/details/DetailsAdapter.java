package com.omarflex5.ui.details;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex5.R;
import com.omarflex5.data.scraper.BaseHtmlParser;

import java.util.ArrayList;
import java.util.List;

public class DetailsAdapter extends RecyclerView.Adapter<DetailsAdapter.ViewHolder> {

    private List<BaseHtmlParser.ParsedItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onClick(BaseHtmlParser.ParsedItem item);
    }

    public DetailsAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<BaseHtmlParser.ParsedItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_resolution_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BaseHtmlParser.ParsedItem item = items.get(position);

        String title = item.getTitle();
        if (title == null || title.isEmpty()) {
            title = "Item " + (position + 1);
        }
        holder.textName.setText(title);

        // Customize badge based on type/quality
        String quality = item.getQuality();
        if (quality != null && !quality.isEmpty()) {
            holder.textBadge.setText(quality);
        } else {
            // Use Type or Index as badge
            holder.textBadge.setText(String.valueOf(position + 1));
        }

        // Info text
        String info = "";
        if (item.getType() != null) {
            info = item.getType().toString();
        } else {
            info = "Click to View";
        }
        holder.textInfo.setText(info);

        holder.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textBadge, textName, textInfo;

        ViewHolder(View itemView) {
            super(itemView);
            textBadge = itemView.findViewById(R.id.text_quality_badge);
            textName = itemView.findViewById(R.id.text_server_name);
            textInfo = itemView.findViewById(R.id.text_server_info);
        }
    }
}

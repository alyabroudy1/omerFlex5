package com.omarflex5.ui.test;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.omarflex5.R;
import com.omarflex5.data.scraper.BaseHtmlParser;

import java.util.ArrayList;
import java.util.List;

public class TestResultAdapter extends RecyclerView.Adapter<TestResultAdapter.ViewHolder> {

    private List<BaseHtmlParser.ParsedItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(BaseHtmlParser.ParsedItem item);
    }

    public void setListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<BaseHtmlParser.ParsedItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void addItems(List<BaseHtmlParser.ParsedItem> newItems) {
        int start = items.size();
        items.addAll(newItems);
        notifyItemRangeInserted(start, newItems.size());
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_test_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BaseHtmlParser.ParsedItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPoster;
        TextView textTitle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPoster = itemView.findViewById(R.id.img_poster);
            textTitle = itemView.findViewById(R.id.text_title);

            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onItemClick(items.get(getAdapterPosition()));
                }
            });
        }

        public void bind(BaseHtmlParser.ParsedItem item) {
            textTitle.setText(item.getTitle() + "\n(" + item.getType() + ")");

            if (item.getPosterUrl() != null && !item.getPosterUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(item.getPosterUrl())
                        .centerCrop()
                        .into(imgPoster);
            }
        }
    }
}

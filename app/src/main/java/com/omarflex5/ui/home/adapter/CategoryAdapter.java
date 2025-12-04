package com.omarflex5.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex5.R;
import com.omarflex5.data.model.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private List<Category> categories = new ArrayList<>();
    private OnCategoryListener listener;
    private int selectedPosition = 0;

    public interface OnCategoryListener {
        void onCategorySelected(Category category);
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        notifyDataSetChanged();
    }

    public void setListener(OnCategoryListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(categories.get(position), position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryText;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryText = itemView.findViewById(R.id.text_category_name);

            categoryText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.post(new Runnable() {
                        @Override
                        public void run() {
                            int position = getAdapterPosition();
                            if (position != RecyclerView.NO_POSITION) {
                                notifyItemChanged(selectedPosition);
                                selectedPosition = position;
                                notifyItemChanged(selectedPosition);
                                if (listener != null) {
                                    listener.onCategorySelected(categories.get(position));
                                }
                            }
                        }
                    });
                }
            });

            // Handle focus as selection for TV
            categoryText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.post(new Runnable() {
                            @Override
                            public void run() {
                                int position = getAdapterPosition();
                                if (position != RecyclerView.NO_POSITION) {
                                    notifyItemChanged(selectedPosition);
                                    selectedPosition = position;
                                    notifyItemChanged(selectedPosition);
                                    if (listener != null) {
                                        listener.onCategorySelected(categories.get(position));
                                    }
                                }
                            }
                        });
                    }
                }
            });
        }

        public void bind(Category category, boolean isSelected) {
            categoryText.setText(category.getName());
            categoryText.setSelected(isSelected);
        }
    }
}

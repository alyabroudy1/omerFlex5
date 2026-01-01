package com.omarflex6.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex6.R;
import com.omarflex6.data.model.Category;

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
        // Use PS4 category item layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_ps4, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category, position, position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        View container;
        TextView categoryText;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.category_container);
            categoryText = itemView.findViewById(R.id.text_category_name);

            if (container == null)
                container = itemView;

            container.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    int oldSelected = selectedPosition;
                    selectedPosition = position;
                    notifyItemChanged(oldSelected);
                    notifyItemChanged(selectedPosition);

                    if (listener != null) {
                        listener.onCategorySelected(categories.get(position));
                    }
                }
            });

            // Focus change listener to trigger selection on focus?
            // Usually on TV, focus = tentative selection, click = confirm.
            // But for sidebar, focus usually updates content immediately.
            // Focus listener removed to prevent auto-selection.
            // Selection now requires explicit click (Enter/Center).
        }

        public void bind(Category category, int position, boolean isSelected) {
            if (categoryText != null) {
                categoryText.setText(category.getName());
            }
            if (container != null) {
                container.setSelected(isSelected);
            }
        }
    }
}

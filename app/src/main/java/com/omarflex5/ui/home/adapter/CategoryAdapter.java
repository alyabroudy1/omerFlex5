package com.omarflex5.ui.home.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex5.R;
import com.omarflex5.data.model.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * CategoryAdapter with distinct focus and selection states:
 * - Focused: visual animation (scale up + pulse) when navigating with D-pad
 * - Selected: persistent red background when item is clicked
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private List<Category> categories = new ArrayList<>();
    private OnCategoryListener listener;
    private int selectedPosition = -1; // -1 means no selection

    public interface OnCategoryListener {
        void onCategorySelected(Category category);
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        selectedPosition = 0; // Select first category by default
        notifyDataSetChanged();
    }

    public void setListener(OnCategoryListener listener) {
        this.listener = listener;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(categories.get(position), position, position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryText;
        AnimatorSet pulseAnimator;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryText = (TextView) itemView;

            // Focus change - visual animation (scale + pulse)
            categoryText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate()
                            .scaleX(1.1f)
                            .scaleY(1.1f)
                            .setDuration(150)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();
                    startPulseAnimation(v);
                } else {
                    stopPulseAnimation();
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start();
                }
            });

            // Click - sets selected state and notifies
            categoryText.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    // Update selected position
                    int oldPosition = selectedPosition;
                    selectedPosition = position;
                    if (oldPosition >= 0) {
                        notifyItemChanged(oldPosition);
                    }
                    notifyItemChanged(selectedPosition);

                    // Notify listener
                    if (listener != null) {
                        listener.onCategorySelected(categories.get(position));
                    }
                }
            });
        }

        private void startPulseAnimation(View v) {
            stopPulseAnimation();

            ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1.1f, 1.13f);
            ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1.1f, 1.13f);
            ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 1.13f, 1.1f);
            ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 1.13f, 1.1f);

            AnimatorSet pulseUp = new AnimatorSet();
            pulseUp.playTogether(scaleUpX, scaleUpY);
            pulseUp.setDuration(400);

            AnimatorSet pulseDown = new AnimatorSet();
            pulseDown.playTogether(scaleDownX, scaleDownY);
            pulseDown.setDuration(400);

            pulseAnimator = new AnimatorSet();
            pulseAnimator.playSequentially(pulseUp, pulseDown);
            pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (pulseAnimator != null && categoryText.hasFocus()) {
                        pulseAnimator.start();
                    }
                }
            });
            pulseAnimator.start();
        }

        private void stopPulseAnimation() {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
            }
        }

        public void bind(Category category, int position, boolean isSelected) {
            categoryText.setText(category.getName());

            // Set selected state for visual styling (red background via selector)
            categoryText.setSelected(isSelected);

            // Constrain left/right within row
            if (position == 0) {
                categoryText.setNextFocusLeftId(categoryText.getId());
            } else {
                categoryText.setNextFocusLeftId(View.NO_ID);
            }

            if (position == getItemCount() - 1) {
                categoryText.setNextFocusRightId(categoryText.getId());
            } else {
                categoryText.setNextFocusRightId(View.NO_ID);
            }
        }
    }
}

package com.omarflex5.ui.home.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex5.R;
import com.omarflex5.data.model.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * CategoryAdapter with distinct focus and selection states:
 * - Position 0: Expanding Search Bar
 * - Position 1+: Category Items
 */
public class CategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_CATEGORY = 1;

    private List<Category> categories = new ArrayList<>();
    private OnCategoryListener listener;
    // Default to index 1 ("All") if available, since "Continue Watching" is 0
    private int selectedPosition = 1;

    // Callback for search submission
    public interface OnCategoryListener {
        void onCategorySelected(Category category);

        void onSearchSubmitted(String query);
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        // Keep selection if valid, otherwise reset to first category
        if (selectedPosition < 0 || selectedPosition >= categories.size()) {
            selectedPosition = 0;
        }
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CategoryViewHolder) {
            ((CategoryViewHolder) holder).bind(categories.get(position), position, position == selectedPosition);
        }
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    // --- VIEW HOLDERS ---

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryText;
        View container;
        AnimatorSet pulseAnimator;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            // PS4 layout: itemView is LinearLayout container, text is inside
            container = itemView;
            categoryText = itemView.findViewById(R.id.text_category_name);

            container.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150)
                            .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                    startPulseAnimation(v);
                } else {
                    stopPulseAnimation();
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });

            container.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    // Update selection
                    int oldPosition = selectedPosition;
                    selectedPosition = position;
                    notifyItemChanged(oldPosition);
                    notifyItemChanged(selectedPosition);

                    if (listener != null) {
                        // Correct index is just position now
                        if (position >= 0 && position < categories.size()) {
                            listener.onCategorySelected(categories.get(position));
                        }
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

            AnimatorSet pulseAnimator = new AnimatorSet();
            pulseAnimator.playSequentially(pulseUp, pulseDown);
            pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (CategoryViewHolder.this.pulseAnimator != null && container.hasFocus()) {
                        CategoryViewHolder.this.pulseAnimator.start();
                    }
                }
            });
            this.pulseAnimator = pulseAnimator;
            this.pulseAnimator.start();
        }

        private void stopPulseAnimation() {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
            }
        }

        public void bind(Category category, int position, boolean isSelected) {
            categoryText.setText(category.getName());
            // Highlight selected category
            if (isSelected) {
                categoryText.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));
                categoryText.setTypeface(null, android.graphics.Typeface.BOLD);
                container.setAlpha(1.0f);
            } else {
                categoryText.setTextColor(itemView.getContext().getResources().getColor(R.color.ps4_text_secondary));
                categoryText.setTypeface(null, android.graphics.Typeface.NORMAL);
                container.setAlpha(0.7f);
            }
        }
    }
}

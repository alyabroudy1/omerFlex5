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

    private static final int VIEW_TYPE_SEARCH = 0;
    private static final int VIEW_TYPE_CATEGORY = 1;

    private List<Category> categories = new ArrayList<>();
    private OnCategoryListener listener;
    private int selectedPosition = 1; // Default to first category (index 1)

    // Callback for search submission
    public interface OnCategoryListener {
        void onCategorySelected(Category category);

        void onSearchSubmitted(String query);
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        // Keep selection if valid, otherwise reset to first category (which is at index
        // 1 now)
        if (selectedPosition < 1 || selectedPosition > categories.size()) {
            selectedPosition = 1;
        }
        notifyDataSetChanged();
    }

    public void setListener(OnCategoryListener listener) {
        this.listener = listener;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    public int getItemViewType(int position) {
        // The first item is always the Search Bar
        return position == 0 ? VIEW_TYPE_SEARCH : VIEW_TYPE_CATEGORY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SEARCH) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_search, parent, false);
            return new SearchViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
            return new CategoryViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SearchViewHolder) {
            ((SearchViewHolder) holder).bind();
        } else if (holder instanceof CategoryViewHolder) {
            // Adjust position for categories list (index 0 in categories is position 1 in
            // adapter)
            int categoryIndex = position - 1;
            ((CategoryViewHolder) holder).bind(categories.get(categoryIndex), position, position == selectedPosition);
        }
    }

    @Override
    public int getItemCount() {
        // Search Item + Categories
        return categories.size() + 1;
    }

    // --- VIEW HOLDERS ---

    class SearchViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView searchIcon;
        EditText searchInput;
        boolean isExpanded = false;
        AnimatorSet pulseAnimator;

        public SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            searchIcon = itemView.findViewById(R.id.img_search_icon);
            searchInput = itemView.findViewById(R.id.edit_search_query);

            // Ensure CardView is focusable
            cardView.setFocusable(true);
            cardView.setFocusableInTouchMode(true);

            // Handle Click to Expand
            cardView.setOnClickListener(v -> toggleExpansion());

            // Handle Focus (D-Pad support)
            cardView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)
                            .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                    startPulseAnimation(v);

                    // If we gained focus, sure simplified state
                    if (isExpanded && !searchInput.hasFocus()) {
                        // Check if input is empty?
                    }
                } else {
                    stopPulseAnimation();
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });

            // Handle Focus Loss on Input (Collapsing logic)
            searchInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    // Logic: If input loses focus, we should check if we need to collapse.
                    // But if user clicked "search", we want to keep it open?
                    // No, usually if you leave the field, you want it to close if empty.
                    String text = searchInput.getText().toString();
                    if (isExpanded && text.isEmpty()) {
                        collapse();
                    } else if (isExpanded) {
                        // If not empty, maybe keep expanded but just hide keyboard?
                        // For TV interface, simpler to just collapse if user navigates away.
                        // But if user navigates to results?
                        // Let's stick to: collapse if empty or manual toggle.
                    }
                }
            });

            // Handle Search Submission
            searchInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_GO ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                                && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    String query = searchInput.getText().toString().trim();
                    if (!query.isEmpty() && listener != null) {
                        listener.onSearchSubmitted(query);
                        // Clear and Collapse
                        searchInput.setText("");
                        collapse();
                    }
                    return true;
                }
                return false;
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
                    if (pulseAnimator != null && cardView.hasFocus()) {
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

        private void toggleExpansion() {
            if (isExpanded) {
                collapse();
            } else {
                expand();
            }
        }

        private void expand() {
            isExpanded = true;
            searchInput.setVisibility(View.VISIBLE);
            searchInput.requestFocus();

            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) itemView
                    .getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }

        private void collapse() {
            isExpanded = false;

            // 1. Hide Keyboard FIRST
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) itemView
                    .getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);

            // 2. Hide Input
            searchInput.setVisibility(View.GONE);

            // 3. Force Focus back to CardView
            // Use post() to ensure layout/visibility changes invoke before focus request
            cardView.post(() -> {
                cardView.requestFocus();
                cardView.requestFocusFromTouch(); // Ensure touch mode doesn't block it
            });
        }

        public void bind() {
            // Reset state if needed
        }
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryText;
        AnimatorSet pulseAnimator;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryText = (TextView) itemView;

            categoryText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)
                            .setInterpolator(new AccelerateDecelerateInterpolator()).start();
                    startPulseAnimation(v);
                } else {
                    stopPulseAnimation();
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                }
            });

            categoryText.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    // Update selection
                    int oldPosition = selectedPosition;
                    selectedPosition = position;
                    notifyItemChanged(oldPosition);
                    notifyItemChanged(selectedPosition);

                    if (listener != null) {
                        // Correct index for listener (subtract 1)
                        int catIndex = position - 1;
                        if (catIndex >= 0 && catIndex < categories.size()) {
                            listener.onCategorySelected(categories.get(catIndex));
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
            categoryText.setSelected(isSelected);
        }
    }
}

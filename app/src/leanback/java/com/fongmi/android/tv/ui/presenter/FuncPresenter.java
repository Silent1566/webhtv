package com.fongmi.android.tv.ui.presenter;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.databinding.AdapterFuncBinding;
import com.fongmi.android.tv.utils.KeyUtil;

public class FuncPresenter extends Presenter {

    private final OnClickListener listener;

    public FuncPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onItemClick(Func item);

        boolean onLongClick(Func item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterFuncBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Func item = (Func) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.text.setText(item.getText());
        holder.binding.icon.setImageResource(item.getDrawable());
        // 打标记供 HomeActivity.dispatchKeyEvent 识别功能行芯片：行级环绕在部分真机上
        // （目标在屏幕外时 grid 级/项级监听拿不到可靠布局回调）仍会吞键，Activity 级
        // 在事件进视图树前接管是最确定的兜底路径。
        holder.view.setTag(R.id.tag_home_func_button, Boolean.TRUE);
        setOnClickListener(holder, view -> listener.onItemClick(item));
        holder.view.setOnLongClickListener(view -> listener.onLongClick(item));
        holder.view.setOnKeyListener((view, keyCode, event) -> onKeyDown(view, event));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    // 首页功能区行已由 CustomRowPresenter.setWrapFocus 在行级接管左右边界（grid 的按键拦截器早于
    // 子项 OnKeyListener），这里的项级环绕退为未开启行级环绕时的落点，不再是该界面实际走到的路径。
    private boolean onKeyDown(android.view.View view, KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event))) return false;
        if (!(view.getParent() instanceof RecyclerView recyclerView) || recyclerView.getAdapter() == null) return false;
        int count = recyclerView.getAdapter().getItemCount();
        if (count <= 1) return false;
        int position = recyclerView.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return false;
        if (KeyUtil.isRightKey(event) && position == count - 1) return requestFocus(recyclerView, 0);
        if (KeyUtil.isLeftKey(event) && position == 0) return requestFocus(recyclerView, count - 1);
        return false;
    }

    private boolean requestFocus(RecyclerView recyclerView, int position) {
        return requestFocus(recyclerView, position, 0);
    }

    /** 行级环绕未开启时的兜底：滚动到目标后逐帧确认焦点真的落地。 */
    private boolean requestFocus(RecyclerView recyclerView, int position, int attempt) {
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null && holder.itemView.requestFocus()) return true;
        if (attempt >= 4) return true;
        recyclerView.scrollToPosition(position);
        recyclerView.post(() -> requestFocus(recyclerView, position, attempt + 1));
        return true;
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterFuncBinding binding;

        public ViewHolder(@NonNull AdapterFuncBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

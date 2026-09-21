package app.onepve.geelyconsole.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import app.onepve.geelyconsole.R;

public class DialogHelper {

    public interface OnDialogClickListener {
        void onClick(Dialog dialog);
    }

    public static class Builder {
        private final Context context;
        private String title;
        private CharSequence message;
        private String positiveText;
        private OnDialogClickListener positiveListener;
        private boolean positiveHighlight = false;
        private String negativeText;
        private OnDialogClickListener negativeListener;
        private String neutralText;
        private OnDialogClickListener neutralListener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder setPositiveButton(String text, OnDialogClickListener listener) {
            this.positiveText = text;
            this.positiveListener = listener;
            return this;
        }

        /** 是否高亮正按钮（蓝色主按钮）；默认白色通用按钮 */
        public Builder setPositiveButtonHighlight(boolean highlight) {
            this.positiveHighlight = highlight;
            return this;
        }

        public Builder setNegativeButton(String text, OnDialogClickListener listener) {
            this.negativeText = text;
            this.negativeListener = listener;
            return this;
        }

        public Builder setNeutralButton(String text, OnDialogClickListener listener) {
            this.neutralText = text;
            this.neutralListener = listener;
            return this;
        }

        public Dialog create() {
            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.layout_custom_dialog);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
            TextView tvMessage = dialog.findViewById(R.id.tv_dialog_message);
            Button btnPositive = dialog.findViewById(R.id.btn_dialog_positive);
            Button btnNegative = dialog.findViewById(R.id.btn_dialog_negative);
            Button btnNeutral = dialog.findViewById(R.id.btn_dialog_neutral);

            if (title != null && !title.isEmpty()) {
                tvTitle.setText(title);
                tvTitle.setVisibility(View.VISIBLE);
            } else {
                tvTitle.setVisibility(View.GONE);
            }

            if (message != null) {
                tvMessage.setText(message);
            }

            if (positiveText != null && !positiveText.isEmpty()) {
                btnPositive.setText(positiveText);
                btnPositive.setVisibility(View.VISIBLE);
                // 地图类（卡主题）→ 高亮蓝色；其他 → 通用白色
                if (positiveHighlight) {
                    btnPositive.setBackgroundResource(R.drawable.btn_primary_bg);
                    btnPositive.setTextColor(Color.WHITE);
                } else {
                    btnPositive.setBackgroundResource(R.drawable.btn_secondary_bg);
                    btnPositive.setTextColor(Color.parseColor("#1E293B"));
                }
                btnPositive.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        if (positiveListener != null) {
                            positiveListener.onClick(dialog);
                        }
                    }
                });
            }

            if (negativeText != null && !negativeText.isEmpty()) {
                btnNegative.setText(negativeText);
                btnNegative.setVisibility(View.VISIBLE);
                btnNegative.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        if (negativeListener != null) {
                            negativeListener.onClick(dialog);
                        }
                    }
                });
            }

            if (neutralText != null && !neutralText.isEmpty()) {
                btnNeutral.setText(neutralText);
                btnNeutral.setVisibility(View.VISIBLE);
                btnNeutral.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        if (neutralListener != null) {
                            neutralListener.onClick(dialog);
                        }
                    }
                });
            }

            return dialog;
        }

        public Dialog show() {
            Dialog dialog = create();
            dialog.show();
            return dialog;
        }
    }
}

package com.example.NotesNest.utils;

import static com.example.NotesNest.utils.Constants.DEFAULT_COLORS;
import static com.example.NotesNest.utils.Constants.professionalGradients;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.NotesNest.R;
import com.example.NotesNest.activity.PremiumActivity;
import com.example.NotesNest.databases.entities.NoteEntity;
import com.example.NotesNest.databases.entities.ReminderEntity;
import com.example.NotesNest.databinding.BottomSheetGradientPickerBinding;
import com.example.NotesNest.databinding.DialogNoteOptionsBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonDialogs {

    public static void showPremiumRequiredDialog(Context context, String message) {
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_premium_required, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextView tvMessage = dialogView.findViewById(R.id.tvMessage);
        Button btnUpgrade = dialogView.findViewById(R.id.btnUpgrade);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        if (tvMessage != null) tvMessage.setText(message);

        if (btnUpgrade != null) {
            btnUpgrade.setOnClickListener(v -> {
                context.startActivity(new Intent(context, PremiumActivity.class));
                dialog.dismiss();
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    public static void showPasswordDialog(Context context, String title, PasswordCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_enter_password, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setText(title);

        TextInputEditText passwordEdit = dialogView.findViewById(R.id.passwordEdit);
        Button btnOk = dialogView.findViewById(R.id.btnOk);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        if (btnOk == null) {
            builder.setPositiveButton("OK", (d, w) -> {
                String pass = Objects.requireNonNull(passwordEdit.getText()).toString();
                if (!pass.isEmpty()) callback.onPasswordEntered(pass);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            btnOk.setOnClickListener(v -> {
                String pass = Objects.requireNonNull(passwordEdit.getText()).toString();
                if (pass.isEmpty()) {
                    Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show();
                } else {
                    callback.onPasswordEntered(pass);
                    dialog.dismiss();
                }
            });
            btnCancel.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        }
    }

    public static void showInputDialog(Context context, String hint,
                                       String posBtn, String negBtn, InputCallback callback) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_category, null);
        builder.setView(view);

        TextInputEditText input = view.findViewById(R.id.etName);
        if (input != null) input.setHint(hint);

        MaterialButton btnAdd = view.findViewById(R.id.btnAdd);
        if (btnAdd != null) btnAdd.setText(posBtn);

        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);
        TextInputLayout tilName = view.findViewById(R.id.tilName);

        AlertDialog dialog = builder.create();
        
        if (btnCancel != null) {
            btnCancel.setText(negBtn);
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnAdd != null) {
            btnAdd.setText(posBtn);
            btnAdd.setOnClickListener(v -> {
                String text = (input != null && input.getText() != null)
                        ? input.getText().toString().trim()
                        : "";

                if (text.isEmpty()) {
                    if (tilName != null) {
                        tilName.setError("Please enter a value");
                    }
                    return; // ❗ stop here, don’t close dialog
                }

                if (tilName != null) tilName.setError(null); // clear error

                callback.onInput(text);
                dialog.dismiss();
            });
        }
    }

    public static void showConfirmDialog(Context context, String title, String message, String posBtn, String negBtn, Runnable onConfirm) {
        showConfirmDialog(context, title, message, posBtn, negBtn, onConfirm, null);
    }

    public static void showConfirmDialog(Context context, String title, String message, String posBtn, String negBtn, Runnable onConfirm, Runnable onCancel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvMessage = view.findViewById(R.id.tvMessage);
        Button btnPositive = view.findViewById(R.id.btnPositive);
        Button btnNegative = view.findViewById(R.id.btnNegative);
        ImageView ivIcon = view.findViewById(R.id.ivIcon);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvMessage != null) tvMessage.setText(message);
        if (btnPositive != null) btnPositive.setText(posBtn);
        if (btnNegative != null) btnNegative.setText(negBtn);

        // Optional: Change icon based on title keywords
        if (ivIcon != null) {
            if (title.toLowerCase(Locale.ROOT).contains("delete") || title.toLowerCase(Locale.ROOT).contains("⚠️")) {
                ivIcon.setImageResource(R.drawable.ic_error_outline);
                ivIcon.setColorFilter(android.graphics.Color.parseColor("#FF5252"));
            } else if (title.toLowerCase(Locale.ROOT).contains("logout")) {
                ivIcon.setImageResource(R.drawable.ic_logout);
            }
        }

        if (btnPositive != null) {
            btnPositive.setOnClickListener(v -> {
                if (onConfirm != null) onConfirm.run();
                dialog.dismiss();
            });
        }

        if (btnNegative != null) {
            btnNegative.setOnClickListener(v -> {
                if (onCancel != null) onCancel.run();
                dialog.dismiss();
            });
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        dialog.show();
    }

    public static void showGradientPicker(Context context, GradientCallback callback) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
        BottomSheetGradientPickerBinding binding = BottomSheetGradientPickerBinding.inflate(
                LayoutInflater.from(context)
        );
        bottomSheetDialog.setContentView(binding.getRoot());

        LinearLayout container = binding.gradientContainer;

        for (int[] colors : professionalGradients) {
            View gradientItem = new View(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 150);
            params.setMargins(0, 16, 0, 16);
            gradientItem.setLayoutParams(params);

            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{colors[0], colors[1]}
            );
            gd.setCornerRadius(24f);
            gradientItem.setBackground(gd);

            gradientItem.setOnClickListener(v -> {
                callback.onGradientSelected(colors[0], colors[1]);
                bottomSheetDialog.dismiss();
            });

            container.addView(gradientItem);
        }

        bottomSheetDialog.show();
    }

    public static void showColorPicker(Context context, String selectedColor, ColorCallback callback) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context, R.style.BottomSheetDialogTheme);
        BottomSheetGradientPickerBinding binding = BottomSheetGradientPickerBinding.inflate(LayoutInflater.from(context));
        bottomSheetDialog.setContentView(binding.getRoot());

        // Rounded corners and background color
        View root = binding.bottomGradientPickerRoot;
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor(selectedColor));
        float radius = 24 * context.getResources().getDisplayMetrics().density;
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        root.setBackground(background);

        // Set height to wrap_content only
        bottomSheetDialog.getBehavior().setPeekHeight(com.google.android.material.bottomsheet.BottomSheetBehavior.PEEK_HEIGHT_AUTO);
        bottomSheetDialog.getBehavior().setFitToContents(true);

        TextView tvTitle = binding.tvTitle;
        tvTitle.setText(R.string.text_choose_note_color);
        tvTitle.setTextColor(Color.BLACK);

        LinearLayout container = binding.gradientContainer;
        container.setPadding(16, 16, 16, 16);

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));

        container.removeAllViews();
        container.addView(recyclerView);

        recyclerView.setAdapter(new RecyclerView.Adapter<ColorViewHolder>() {
            @NonNull
            @Override
            public ColorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View colorView = new View(context);
                int size = (int) (56 * context.getResources().getDisplayMetrics().density);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(size, size);
                params.setMargins(12, 12, 12, 12);
                colorView.setLayoutParams(params);
                return new ColorViewHolder(colorView);
            }

            @Override
            public void onBindViewHolder(@NonNull ColorViewHolder holder, int position) {
                String colorHex = DEFAULT_COLORS[position];
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(Color.parseColor(colorHex));
                gd.setShape(GradientDrawable.OVAL);

                if (colorHex.equalsIgnoreCase(selectedColor)) {
                    gd.setStroke(6, Color.WHITE);
                }

                holder.itemView.setBackground(gd);
                holder.itemView.setElevation(4f);
                holder.itemView.setOnClickListener(v -> {
                    callback.onColorSelected(colorHex);
                    bottomSheetDialog.dismiss();
                });
            }

            @Override
            public int getItemCount() {
                return DEFAULT_COLORS.length;
            }
        });

        bottomSheetDialog.show();
    }

    static class ColorViewHolder extends RecyclerView.ViewHolder {
        public ColorViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static void showCategoryDialog(Context context, String title, List<String> categories, int preselect, CategoryCallback callback) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(categories.toArray(new String[0]), preselect, (dialog, which) -> {
                    callback.onCategorySelected(categories.get(which), which);
                    dialog.dismiss();
                })
                .show();
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static void showNoteContentDialog(Context context, NoteEntity note, NoteDialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_note_full_content, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextView title = view.findViewById(R.id.tvTitle);
        TextView content = view.findViewById(R.id.tvMessage);
        TextView date = view.findViewById(R.id.tvDate);
        TextView time = view.findViewById(R.id.tvTime);
        TextView category = view.findViewById(R.id.tvCategory);
        ImageButton btnShare = view.findViewById(R.id.btnShare);
        ImageView ivPinned = view.findViewById(R.id.ivPinned);
        androidx.cardview.widget.CardView card = view.findViewById(R.id.dialogNote);

        title.setText(note.title);
        ivPinned.setVisibility(note.isPinned ? View.VISIBLE : View.GONE);
        ivPinned.setImageResource(note.isPinned ? R.drawable.ic_pinned : R.drawable.ic_unpinned);

        setupResponsiveCheckboxes(context, content, note, callback);

        callback.setDateTime(note.createdAt, date, time);
        callback.setCategory(category, note.categoryId);

        try {
            int color = android.graphics.Color.parseColor(note.colorHex);
            card.setCardBackgroundColor(color);
        } catch (Exception ignored) {}

        btnShare.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
            View sheetView = LayoutInflater.from(context).inflate(R.layout.share_bottom_sheet, null);
            bottomSheetDialog.setContentView(sheetView);

            NoteShareManager shareManager = new NoteShareManager(context);

            sheetView.findViewById(R.id.share_text).setOnClickListener(stView -> {
                stView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                bottomSheetDialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, note.title);
                intent.putExtra(Intent.EXTRA_TEXT, note.title + "\n\n" + Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY));
                context.startActivity(Intent.createChooser(intent, "Share Note as Text"));
            });

            sheetView.findViewById(R.id.share_image).setOnClickListener(imView -> {
                imView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                bottomSheetDialog.dismiss();
                shareManager.shareAsImage(note, card);
            });

            sheetView.findViewById(R.id.share_pdf).setOnClickListener(pdfView -> {
                pdfView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                bottomSheetDialog.dismiss();
                shareManager.shareAsPdf(note, card);
            });

            bottomSheetDialog.show();
        });

        dialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static void setupResponsiveCheckboxes(Context context, TextView tv, NoteEntity note, NoteDialogCallback callback) {
        String converted = HtmlListConverter.convertHtmlLists(note.content);
        SpannableStringBuilder builder = new SpannableStringBuilder(Html.fromHtml(converted, Html.FROM_HTML_MODE_LEGACY));

        // Find all checkbox characters (☐ and ☑)
        String text = builder.toString();
        Pattern pattern = Pattern.compile("[☐☑]");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            final int start = matcher.start();
            final int end = matcher.end();
            final char icon = text.charAt(start);

            // Increase size of the checkbox icon
            builder.setSpan(new AbsoluteSizeSpan(22, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            builder.setSpan(new ClickableSpan() {
                @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                @Override
                public void onClick(@NonNull View widget) {
                    toggleNoteCheckbox(context, note, start, icon == '☐', callback, tv);
                }

                @Override
                public void updateDrawState(@NonNull android.text.TextPaint ds) {
                    ds.setUnderlineText(false);
                    ds.setColor(Color.BLACK);
                }
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        tv.setText(builder);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static void toggleNoteCheckbox(Context context, NoteEntity note, int charPos, boolean shouldCheck, NoteDialogCallback callback, TextView tv) {
        String html = note.content;
        Pattern pattern = Pattern.compile("<input[^>]*type=\"checkbox\"[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);

        int clickedIndex = 0;
        String convertedText = tv.getText().toString();
        for (int i = 0; i < charPos; i++) {
            char c = convertedText.charAt(i);
            if (c == '☐' || c == '☑') clickedIndex++;
        }

        StringBuilder sb = new StringBuilder();
        int currentIndex = 0;
        while (matcher.find()) {
            if (currentIndex == clickedIndex) {
                String tag = matcher.group();
                String newTag;
                if (shouldCheck) {
                    if (!tag.toLowerCase(Locale.ROOT).contains("checked")) {
                        newTag = tag.replace(">", " checked>");
                    } else {
                        newTag = tag;
                    }
                } else {
                    newTag = tag.replaceAll("(?i)\\s*checked(=[\"']?checked[\"']?)?", "");
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(newTag));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
            currentIndex++;
        }
        matcher.appendTail(sb);

        // Update the note object
        note.content = sb.toString();
        note.updatedAt = System.currentTimeMillis();

        // CRITICAL: Call the update on the ViewModel/Repository via callback
        if (callback instanceof NoteActionCallback) {
            ((NoteActionCallback) callback).onNoteUpdated(note);
        }

        // Refresh the UI in the dialog immediately
        setupResponsiveCheckboxes(context, tv, note, callback);
    }

    public static void showOptionsDialog(View anchorView, NoteEntity note, int pos, NoteOptionsListener listener) {
        Context context = anchorView.getContext();
        DialogNoteOptionsBinding binding = DialogNoteOptionsBinding.inflate(LayoutInflater.from(context));
        View view = binding.getRoot();

        PopupWindow popupWindow = new PopupWindow(view,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(40f);

        // Make the note dull when popup is shown
        anchorView.setAlpha(0.4f);

        // Restore alpha when popup is dismissed
        popupWindow.setOnDismissListener(() -> anchorView.setAlpha(1.0f));

        view.findViewById(R.id.btnEdit).setOnClickListener(v -> {
            listener.onEdit(note);
            popupWindow.dismiss();
        });

        LinearLayout btnPin = view.findViewById(R.id.btnPin);
        TextView tvPinText = view.findViewById(R.id.tvPinText);
        ImageView ivPinIcon = view.findViewById(R.id.ivPinIcon);

        if (note.isPinned) {
            tvPinText.setText(R.string.text_unpin);
            ivPinIcon.setImageResource(R.drawable.ic_unpinned);
        } else {
            tvPinText.setText(R.string.text_pin);
            ivPinIcon.setImageResource(R.drawable.ic_pinned);
        }

        btnPin.setOnClickListener(v -> {
            listener.onPin(note);
            popupWindow.dismiss();
        });

        view.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            listener.onDelete(note, pos);
            popupWindow.dismiss();
        });

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int xOffset = (anchorView.getWidth() - view.getMeasuredWidth()) / 2;
        int yOffset = -(anchorView.getHeight() + view.getMeasuredHeight()) / 2;

        popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
    }

    public static void showCustomDialog(Context context, ReminderEntity reminder, String posBtn, String negBtn, Runnable onEdit, Runnable onDelete) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_reminder_options, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        TextView tvMessage = view.findViewById(R.id.tvMessage);
        Button btnEdit = view.findViewById(R.id.btnEdit);
        Button btnDelete = view.findViewById(R.id.btnDelete);
        ImageButton btnClose = view.findViewById(R.id.btnClose);
        View layout = view.findViewById(R.id.reminderLayout);

        tvTitle.setText(reminder.title);
        tvMessage.setText(reminder.message);
        btnEdit.setText(posBtn);
        btnDelete.setText(negBtn);

        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{reminder.gradientStartColor, reminder.gradientEndColor}
            );
        gd.setCornerRadius(24f);
        layout.setBackground(gd);

        btnEdit.setOnClickListener(v -> {
            onEdit.run();
            dialog.dismiss();
        });
        btnDelete.setOnClickListener(v -> {
            onDelete.run();
            dialog.dismiss();
        });
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public static AlertDialog showProgressDialog(Context context, String message) {
        if (context instanceof Activity && ((Activity) context).isFinishing()) return null;

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null);
        TextView tvLoadingMessage = view.findViewById(R.id.tvLoadingMessage);
        if (tvLoadingMessage != null) tvLoadingMessage.setText(message);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Ensure the window itself doesn't have extra margins and centers correctly
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        dialog.show();
        return dialog;
    }

    public static void showErrorDialog(Context context, String title, String message) {
        if (context instanceof Activity && ((Activity) context).isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_error, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextView tvTitle = view.findViewById(R.id.tvErrorTitle);
        TextView tvMessage = view.findViewById(R.id.tvErrorMessage);
        Button btnDismiss = view.findViewById(R.id.btnDismiss);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvMessage != null) tvMessage.setText(message);

        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> dialog.dismiss());
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        dialog.show();
    }

    public static void showChangePasswordDialog(Context context, ChangePasswordCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_change_password, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextInputEditText currentPassword = view.findViewById(R.id.etCurrentPassword);
        TextInputEditText newPassword = view.findViewById(R.id.etNewPassword);
        TextInputEditText confirmPassword = view.findViewById(R.id.etConfirmPassword);
        
        TextInputLayout tilNew = view.findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirm = view.findViewById(R.id.tilConfirmPassword);

        Button btnUpdate = view.findViewById(R.id.btnUpdate);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(v -> {
                String currentPass = Objects.requireNonNull(currentPassword.getText()).toString().trim();
                String newPass = Objects.requireNonNull(newPassword.getText()).toString().trim();
                String confirmPass = Objects.requireNonNull(confirmPassword.getText()).toString().trim();

                if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (newPass.length() < 6) {
                    if (tilNew != null) tilNew.setError("Password must be at least 6 characters");
                    return;
                } else {
                    if (tilNew != null) tilNew.setError(null);
                }

                if (!newPass.equals(confirmPass)) {
                    if (tilConfirm != null) tilConfirm.setError("Passwords do not match");
                    return;
                } else {
                    if (tilConfirm != null) tilConfirm.setError(null);
                }

                callback.onUpdate(currentPass, newPass);
                dialog.dismiss();
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        
        dialog.show();
    }

    public static void showReauthenticationDialog(Context context, ReAuthCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_reauthenticate, null);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        TextInputEditText passwordEdit = view.findViewById(R.id.etPassword);
        TextInputLayout tilPassword = view.findViewById(R.id.tilPassword);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                String pass = Objects.requireNonNull(passwordEdit.getText()).toString().trim();
                if (!pass.isEmpty()) {
                    callback.onConfirm(pass);
                    dialog.dismiss();
                } else {
                    if (tilPassword != null) tilPassword.setError("Password required");
                }
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.CENTER);
        }

        dialog.show();
    }

    public interface PasswordCallback { void onPasswordEntered(String password); }
    public interface InputCallback { void onInput(String text); }
    public interface GradientCallback { void onGradientSelected(int startColor, int endColor); }
    public interface ColorCallback { void onColorSelected(String color); }
    public interface CategoryCallback { void onCategorySelected(String category, int position); }
    public interface ChangePasswordCallback { void onUpdate(String currentPass, String newPass); }
    public interface ReAuthCallback { void onConfirm(String password); }

    public interface NoteDialogCallback {
        void setDateTime(long timeStamp, TextView dateView, TextView timeView);
        void setCategory(TextView categoryView, String categoryId);
    }

    public interface NoteActionCallback extends NoteDialogCallback {
        void onNoteUpdated(NoteEntity note);
    }

    public interface NoteOptionsListener {
        void onEdit(NoteEntity note);
        void onDelete(NoteEntity note, int pos);
        default void onPin(NoteEntity note) {}
    }
}

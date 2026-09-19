package com.example.NotesNest.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import androidx.core.content.FileProvider;

import com.example.NotesNest.R;
import com.example.NotesNest.databases.entities.NoteEntity;

import java.io.File;
import java.io.FileOutputStream;

public class NoteShareManager {

    private final Context context;

    public NoteShareManager(Context context) {
        this.context = context;
    }

    // 2. CAPTURE VIEW AS BITMAP
    public Bitmap captureViewAsBitmap(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }


    // 3. SAVE BITMAP AS IMAGE (PNG)
    public File saveBitmapAsImage(Bitmap bitmap, String fileName) throws Exception {
        File file = new File(context.getExternalFilesDir(null), fileName + ".png");
        FileOutputStream out = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.flush();
        out.close();
        return file;
    }

    // 4. CREATE PDF FROM BITMAP
    public File createPdfFromBitmap(Bitmap bitmap, String fileName, int backgroundColor) throws Exception {
        File pdfFile = new File(context.getExternalFilesDir(null), fileName + ".pdf");

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(bitmap.getWidth(), bitmap.getHeight(), 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        canvas.drawColor(backgroundColor);
        canvas.drawBitmap(bitmap, 0f, 0f, null);
        document.finishPage(page);

        FileOutputStream out = new FileOutputStream(pdfFile);
        document.writeTo(out);
        document.close();
        out.close();

        return pdfFile;
    }


    // 5. SHARE IMAGE VIA INTENT
    public void shareImage(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(share, "Share Image"));

        } catch (Exception e) {
            Log.e("NoteShareManager", "Error while sharing image: " + e.getMessage(), e);
        }
    }

    // 6. SHARE PDF VIA INTENT
    public void sharePdf(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(share, "Share PDF"));

        } catch (Exception e) {
            Log.e("NoteShareManager", "Error while sharing PDF: " + e.getMessage(), e);
        }
    }

    public Bitmap getRoundedCornerBitmap(Bitmap bitmap, float cornerRadius) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        RectF rectF = new RectF(rect);
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(Color.RED);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    // 7. SHARE NOTE AS IMAGE (FROM TITLE/CONTENT)
    public void shareAsImage(NoteEntity note, View noteView) {
        try {
            View shareButton = noteView.findViewById(R.id.btnShare);
            int previousVisibility = shareButton != null ? shareButton.getVisibility() : View.VISIBLE;
            if (shareButton != null) {
                shareButton.setVisibility(View.GONE);
            }

            Bitmap noteBitMap = captureViewAsBitmap(noteView);
            Bitmap roundedBitmap = getRoundedCornerBitmap(noteBitMap, 32f);

            if (shareButton != null) {
                shareButton.setVisibility(previousVisibility);
            }

            File file = saveBitmapAsImage(roundedBitmap, note.title);
            shareImage(file);

        } catch (Exception e) {
            Log.e("NoteShareManager", "Error while sharing note as image: " + e.getMessage(), e);
        }
    }

    // 8. SHARE NOTE AS PDF (FROM TITLE/CONTENT)
    public void shareAsPdf(NoteEntity note, View noteView) {
        try {
            View shareButton = noteView.findViewById(R.id.btnShare);
            int previousVisibility = shareButton != null ? shareButton.getVisibility() : View.VISIBLE;
            if (shareButton != null) {
                shareButton.setVisibility(View.GONE);
            }

            int backgroundColor = Color.WHITE;
            if (noteView instanceof androidx.cardview.widget.CardView) {
                backgroundColor = ((androidx.cardview.widget.CardView) noteView).getCardBackgroundColor().getDefaultColor();
            }

            Bitmap noteBitMap = captureViewAsBitmap(noteView);

            if (shareButton != null) {
                shareButton.setVisibility(previousVisibility);
            }

            File pdfFile = createPdfFromBitmap(noteBitMap, note.title, backgroundColor);
            sharePdf(pdfFile);

        } catch (Exception e) {
            Log.e("NoteShareManager", "Error while sharing note as pdf: " + e.getMessage(), e);
        }
    }
}

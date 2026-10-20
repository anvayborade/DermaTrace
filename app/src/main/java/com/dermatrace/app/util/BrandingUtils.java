package com.dermatrace.app.util;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import androidx.core.content.ContextCompat;
import com.dermatrace.app.R;

public final class BrandingUtils {
    private BrandingUtils() {}
    public static CharSequence wordmark(Context context) {
        SpannableString mark = new SpannableString("DermaTrace");
        mark.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.dt_text)),
                0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mark.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.dt_primary)),
                5, mark.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return mark;
    }
}

package org.lineageos.settings.xiaomiparts;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.view.Gravity;

import org.lineageos.settings.R;

public class SplashActivity extends Activity {

    private static final int SPLASH_DURATION = 5000;
    private ValueAnimator heartbeatAnimator;
    private ValueAnimator textAnimator;
    private ImageView logoView;
    private TextView titleText;
    private TextView creditText;
    private int logoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createAndSetSplashLayout();
        startHeartbeatPulse(logoView);
        startTextAnimation();
        startCreditHighlightAnimation();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (heartbeatAnimator != null) heartbeatAnimator.cancel();
            if (textAnimator != null) textAnimator.cancel();

            Intent intent = new Intent(SplashActivity.this, XiaomiPartsActivity.class);
            startActivity(intent);
            finish();
        }, SPLASH_DURATION);
    }

    private void createAndSetSplashLayout() {
        RelativeLayout layout = new RelativeLayout(this);
        layout.setBackgroundColor(Color.parseColor("#FF6900"));

        logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoId = View.generateViewId();  // Generate unique ID
        logoView.setId(logoId);

        RelativeLayout.LayoutParams logoParams = new RelativeLayout.LayoutParams(
                dpToPx(120), dpToPx(120));
        logoParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        logoView.setLayoutParams(logoParams);
        logoView.setImageResource(R.drawable.ic_launcher_icon);

        titleText = new TextView(this);
        titleText.setText("XiaomiParts");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(24);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setAlpha(0f);

        RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        textParams.addRule(RelativeLayout.BELOW, logoId);
        textParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        textParams.topMargin = dpToPx(20);
        titleText.setLayoutParams(textParams);

        // Alsó credit szöveg (designeg by bezke®)
	creditText = new TextView(this);
	creditText.setText("designed by bezke®");
	creditText.setTextColor(Color.WHITE);
	creditText.setTextSize(14);
	creditText.setTypeface(Typeface.DEFAULT_BOLD);
	creditText.setGravity(Gravity.CENTER); // Ez biztosítja a középre igazítást
	RelativeLayout.LayoutParams creditParams = new RelativeLayout.LayoutParams(
       	 RelativeLayout.LayoutParams.MATCH_PARENT,
       	 RelativeLayout.LayoutParams.WRAP_CONTENT);
	creditParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
	creditParams.addRule(RelativeLayout.CENTER_HORIZONTAL); // Ez is segíthet
	creditParams.bottomMargin = dpToPx(12);
	creditText.setLayoutParams(creditParams);
	layout.addView(logoView);
	layout.addView(titleText);
	layout.addView(creditText);
	setContentView(layout);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void startHeartbeatPulse(final ImageView view) {
        heartbeatAnimator = ValueAnimator.ofFloat(0f, 1f);
        heartbeatAnimator.setDuration(950);
        heartbeatAnimator.setRepeatCount(ValueAnimator.INFINITE);

        heartbeatAnimator.addUpdateListener(animator -> {
            float progress = animator.getAnimatedFraction();
            float scale = 1.0f;

            if (progress <= 0.21f) {
                float pulseProgress = progress / 0.21f;
                scale = pulseProgress <= 0.5f
                        ? 1.0f + (pulseProgress * 2 * 0.3f)
                        : 1.3f - ((pulseProgress - 0.5f) * 2 * 0.3f);
            } else if (progress >= 0.47f && progress <= 0.63f) {
                float pulseProgress = (progress - 0.47f) / 0.16f;
                scale = pulseProgress <= 0.5f
                        ? 1.0f + (pulseProgress * 2 * 0.15f)
                        : 1.15f - ((pulseProgress - 0.5f) * 2 * 0.15f);
            }

            view.setScaleX(scale);
            view.setScaleY(scale);
        });

        heartbeatAnimator.start();
    }

    private void startTextAnimation() {
        final String fullText = "XiaomiParts";
        final int totalDuration = 3000;
        final int charDelay = totalDuration / fullText.length();

        textAnimator = ValueAnimator.ofInt(0, fullText.length());
        textAnimator.setDuration(totalDuration);

        textAnimator.addUpdateListener(animator -> {
            int currentLength = (Integer) animator.getAnimatedValue();
            String currentText = fullText.substring(0, currentLength);
            titleText.setText(currentText);
            if (currentLength > 0) {
                titleText.setAlpha(1f);
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(textAnimator::start, 500);
    }

    private void startCreditHighlightAnimation() {
        // Fénysugár animáció designed by bezke® feliratra
        final String credit = "designed by bezke®";
        final TextView textView = creditText;

        final ValueAnimator shimmerAnimator = ValueAnimator.ofFloat(0, 1);
        shimmerAnimator.setDuration(4500);
        shimmerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shimmerAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        shimmerAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            int width = textView.getWidth();

            LinearGradient shader = new LinearGradient(
                    width * progress, 0,
                    width * progress + width / 3f, 0,
                    new int[]{Color.TRANSPARENT, Color.WHITE, Color.TRANSPARENT},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            );

            textView.getPaint().setShader(shader);
            textView.invalidate();

            if (progress >= 0.9f) {
                // Csillanás a ® karakteren
                SpannableString span = new SpannableString(credit);
                int sparkleIndex = credit.indexOf("®");
                if (sparkleIndex >= 0) {
                    span.setSpan(new ForegroundColorSpan(Color.YELLOW),
                            sparkleIndex, sparkleIndex + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    textView.setText(span);
                }
            } else {
                textView.setText(credit);
            }
        });

        // Kis késleltetéssel induljon, hogy látványosabb legyen
        new Handler(Looper.getMainLooper()).postDelayed(shimmerAnimator::start, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (heartbeatAnimator != null) heartbeatAnimator.cancel();
        if (textAnimator != null) textAnimator.cancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (heartbeatAnimator != null) heartbeatAnimator.cancel();
        if (textAnimator != null) textAnimator.cancel();
    }

    @Override
    public void onBackPressed() {
        // Splash screen alatt nincs visszalépés
    }
}

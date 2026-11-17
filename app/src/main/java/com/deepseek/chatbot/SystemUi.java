package com.deepseek.chatbot;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

final class SystemUi {
    private static final int LIGHT_BACKGROUND = Color.rgb(250, 250, 252);
    private static final int DARK_BACKGROUND = Color.rgb(18, 19, 22);

    private SystemUi() {
    }

    static boolean isDark(Activity activity) {
        int mode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    static int backgroundColor(boolean dark) {
        return dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }

    static int primaryTextColor(Activity activity) {
        return isDark(activity) ? Color.rgb(242, 243, 245) : Color.rgb(28, 29, 33);
    }

    static int secondaryTextColor(Activity activity) {
        return isDark(activity) ? Color.rgb(167, 170, 179) : Color.rgb(99, 103, 115);
    }

    static void installInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets insets = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = insets.left;
                top = insets.top;
                right = insets.right;
                bottom = insets.bottom;
            } else {
                left = windowInsets.getSystemWindowInsetLeft();
                top = windowInsets.getSystemWindowInsetTop();
                right = windowInsets.getSystemWindowInsetRight();
                bottom = windowInsets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    static void applyTheme(Activity activity, boolean dark) {
        int background = backgroundColor(dark);
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setNavigationBarColor(background);
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(background);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
        } else {
            View decor = activity.getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(dark ? flags & ~lightFlags : flags | lightFlags);
        }
    }
}

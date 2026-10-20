package com.dermatrace.app.util;

import android.app.Activity;
import android.content.Intent;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.dermatrace.app.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executors;

public final class AuthUtils {

    private AuthUtils() {}

    public static String displayName(FirebaseUser user) {
        if (user == null) return "User";

        String name = user.getDisplayName();
        if (name != null && !name.trim().isEmpty()) return name.trim();

        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).trim();
            if (!local.isEmpty()) {
                if (local.length() == 1) return local.toUpperCase();
                return Character.toUpperCase(local.charAt(0)) + local.substring(1);
            }
        }
        return "User";
    }

    public static String firstName(FirebaseUser user) {
        String full = displayName(user).trim();
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    public static String providerLabel(FirebaseUser user) {
        if (user == null) return "Not signed in";
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            String provider = info.getProviderId();
            if ("google.com".equals(provider)) return "Google account";
            if ("password".equals(provider)) return "Email & password";
        }
        return "Firebase account";
    }

    public static void signOut(Activity activity) {
        FirebaseAuth.getInstance().signOut();

        try {
            CredentialManager manager = CredentialManager.create(activity);
            manager.clearCredentialStateAsync(
                    new ClearCredentialStateRequest(),
                    new CancellationSignal(),
                    Executors.newSingleThreadExecutor(),
                    new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override public void onResult(@NonNull Void result) {}
                        @Override public void onError(@NonNull ClearCredentialException e) {}
                    }
            );
        } catch (Exception ignored) {}

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}

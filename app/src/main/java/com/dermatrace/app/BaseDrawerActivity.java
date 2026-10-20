package com.dermatrace.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.dermatrace.app.util.AuthUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;

/**
 * Shared navigation shell for every normal DermaTrace screen.
 * Camera capture intentionally stays full-screen and does not inherit this class.
 */
public abstract class BaseDrawerActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    protected DrawerLayout drawerLayout;
    protected NavigationView navigationView;
    protected MaterialToolbar drawerToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }
    }

    protected void setDrawerContentView(@LayoutRes int layoutResId, int toolbarId) {
        super.setContentView(R.layout.activity_base_drawer);

        drawerLayout = findViewById(R.id.baseDrawerLayout);
        navigationView = findViewById(R.id.baseNavigationView);
        FrameLayout contentHost = findViewById(R.id.baseContentHost);

        LayoutInflater.from(this).inflate(layoutResId, contentHost, true);

        drawerToolbar = findViewById(toolbarId);
        setSupportActionBar(drawerToolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                drawerToolbar,
                R.string.navigation_open,
                R.string.navigation_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        View header = navigationView.getHeaderView(0);
        if (header != null) {
            TextView branding = header.findViewById(R.id.txtNavBrand);
            if (branding != null) branding.setText(brandTitle());
        }
        if (drawerToolbar != null && "DermaTrace".contentEquals(drawerToolbar.getTitle())) {
            drawerToolbar.setTitle(brandTitle());
        }
        if (header != null && currentUser != null) {
            TextView navName = header.findViewById(R.id.txtNavUserName);
            TextView navEmail = header.findViewById(R.id.txtNavUserEmail);
            if (navName != null) navName.setText(AuthUtils.displayName(currentUser));
            if (navEmail != null) {
                String mail = currentUser.getEmail();
                navEmail.setText(mail == null || mail.trim().isEmpty()
                        ? AuthUtils.providerLabel(currentUser) : mail);
            }
        }

        // Android 15+ enforces edge-to-edge. Apply real system-bar insets instead
        // of hard-coded status/navigation margins so content never sits underneath them.
        ViewCompat.setOnApplyWindowInsetsListener(contentHost, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(0, bars.top, 0, Math.max(bars.bottom, ime.bottom));   // keep buttons above the keyboard
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
            return insets;
        });

        ViewCompat.requestApplyInsets(contentHost);
        bindBottomNavigation();
    }

    private void bindBottomNavigation() {
        View home = findViewById(R.id.navBottomHome);
        View patients = findViewById(R.id.navBottomPatients);
        View analyze = findViewById(R.id.navBottomAnalyze);
        View reports = findViewById(R.id.navBottomReports);
        View more = findViewById(R.id.navBottomMore);
        if (home == null) return;
        home.setOnClickListener(v -> openBottom(MainActivity.class));
        patients.setOnClickListener(v -> openBottom(PatientsActivity.class));
        analyze.setOnClickListener(v -> openBottom(NewAnalysisActivity.class));
        reports.setOnClickListener(v -> openBottom(ReportsActivity.class));
        more.setOnClickListener(v -> { if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START); });
        updateBottomSelection();
    }

    private void updateBottomSelection() {
        int active = ContextCompat.getColor(this, R.color.dt_primary);
        int idle = ContextCompat.getColor(this, R.color.dt_text_muted);
        boolean home = this instanceof MainActivity;
        boolean patients = this instanceof PatientsActivity;
        boolean analyze = this instanceof NewAnalysisActivity;
        boolean reports = this instanceof ReportsActivity;
        setBottomState(R.id.navBottomHomeIcon, R.id.navBottomHomeText, home, active, idle);
        setBottomState(R.id.navBottomPatientsIcon, R.id.navBottomPatientsText, patients, active, idle);
        setBottomState(R.id.navBottomAnalyzeIcon, R.id.navBottomAnalyzeText, analyze, active, idle);
        setBottomState(R.id.navBottomReportsIcon, R.id.navBottomReportsText, reports, active, idle);
        setBottomState(R.id.navBottomMoreIcon, R.id.navBottomMoreText, false, active, idle);
    }

    private void setBottomState(int imageId, int textId, boolean selected, int active, int idle) {
        ImageView image = findViewById(imageId);
        TextView text = findViewById(textId);
        if (image != null) image.setColorFilter(selected ? active : idle);
        if (text != null) {
            text.setTextColor(selected ? active : idle);
            text.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void openBottom(Class<?> destination) {
        if (destination.equals(getClass())) return;
        Intent intent = new Intent(this, destination);
        if (destination == MainActivity.class) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    protected CharSequence brandTitle() {
        return com.dermatrace.app.util.BrandingUtils.wordmark(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_new_analysis) {
            if (!(this instanceof NewAnalysisActivity)) startActivity(new Intent(this, NewAnalysisActivity.class));
            return true;
        }
        if (id == R.id.menu_reports) {
            if (!(this instanceof ReportsActivity)) startActivity(new Intent(this, ReportsActivity.class));
            return true;
        }
        if (id == R.id.menu_settings) {
            if (!(this instanceof SettingsActivity)) startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.menu_about) {
            if (!(this instanceof AboutActivity)) startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Class<?> destination = null;

        if (id == R.id.nav_dashboard) destination = MainActivity.class;
        else if (id == R.id.nav_new_analysis) destination = NewAnalysisActivity.class;
        else if (id == R.id.nav_patients) destination = PatientsActivity.class;
        else if (id == R.id.nav_history) destination = HistoryActivity.class;
        else if (id == R.id.nav_reports) destination = ReportsActivity.class;
        else if (id == R.id.nav_compare) destination = CompareHubActivity.class;
        else if (id == R.id.nav_followups) destination = FollowUpsActivity.class;
        else if (id == R.id.nav_nearby) destination = NearbyCareActivity.class;
        else if (id == R.id.nav_settings) destination = SettingsActivity.class;
        else if (id == R.id.nav_about) destination = AboutActivity.class;
        else if (id == R.id.nav_logout) {
            if (drawerLayout != null) drawerLayout.closeDrawers();
            new AlertDialog.Builder(this)
                    .setTitle("Sign out?")
                    .setMessage("Your local DermaTrace records stay on this device. You will need to sign in again to access the app.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Sign out", (dialog, which) -> AuthUtils.signOut(this))
                    .show();
            return true;
        }

        if (destination != null && !destination.equals(getClass())) {
            Intent intent = new Intent(this, destination);
            if (destination == MainActivity.class) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            startActivity(intent);
        }

        if (drawerLayout != null) drawerLayout.closeDrawers();
        return true;
    }
}

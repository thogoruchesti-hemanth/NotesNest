package com.example.NotesNest.fragments;

import static com.example.NotesNest.utils.Constants.EXTRA_REMINDER_ID;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.NotesNest.R;
import com.example.NotesNest.activity.EditReminderActivity;
import com.example.NotesNest.adapter.CalendarAdapter;
import com.example.NotesNest.adapter.TimelineAdapter;
import com.example.NotesNest.databases.ViewModels.ReminderViewModel;
import com.example.NotesNest.databases.entities.ReminderEntity;
import com.example.NotesNest.models.CalendarItem;
import com.example.NotesNest.models.Task;
import com.example.NotesNest.utils.AnalyticsHelper;
import com.example.NotesNest.utils.AppPreferences;
import com.example.NotesNest.utils.CommonDialogs;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class RemindersFragment extends Fragment {

    private final List<CalendarItem> calendarItemList = new ArrayList<>();
    private TextView selectedDateTv, promptTextView;
    private RecyclerView calendarRv, hourRecyclerView;
    private LocalDate selectedDate = LocalDate.now();
    private CalendarAdapter calendarAdapter;
    private ReminderViewModel reminderViewModel;
    private List<ReminderEntity> currentReminders = new ArrayList<>();
    private List<ReminderEntity> allRemindersList = new ArrayList<>();
    private String currentUserId = null;
    boolean isPremium;
    private static final int FREE_REMINDER_LIMIT = 30;
    private AdView adView;
    private View emptyStateLayout;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_reminders, container, false);

        // Initialize ViewModel
        reminderViewModel = new ViewModelProvider(this).get(ReminderViewModel.class);

        selectedDateTv = view.findViewById(R.id.tvSelectedDate);
        promptTextView = view.findViewById(R.id.tvPrompt);
        calendarRv = view.findViewById(R.id.calendarRecyclerView);
        hourRecyclerView = view.findViewById(R.id.hourRecyclerView);
        emptyStateLayout = view.findViewById(R.id.emptyStateReminders);
        Button btnEmptyCreate = view.findViewById(R.id.btnEmptyCreateReminder);
        Button createButton = view.findViewById(R.id.createButton);
        adView = view.findViewById(R.id.adViewReminders);

        currentUserId = AppPreferences.getInstance().getUserId();
        isPremium = AppPreferences.getInstance().isUserPremium();

        setupCalendar();
        updateSelectedDateText();
        loadMonthData();
        setupObservers();

        selectedDateTv.setOnClickListener(v -> showDatePicker());

        View.OnClickListener createListener = v -> {

            if (!isPremium && allRemindersList.size() >= FREE_REMINDER_LIMIT) {
                CommonDialogs.showPremiumRequiredDialog(requireContext(),"Free users can create up to 30 reminders.\nUpgrade to Premium for unlimited reminders.");
                return;
            }

            Intent intent = new Intent(requireContext(), EditReminderActivity.class);
            intent.putExtra("selected_date", selectedDate.toString());
            startActivity(intent);
        };

        createButton.setOnClickListener(createListener);
        if (btnEmptyCreate != null) btnEmptyCreate.setOnClickListener(createListener);

        setupBannerAd();

        return view;
    }

    private void setupBannerAd() {
        if (isPremium) {
            adView.setVisibility(View.GONE);
            return;
        }
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    @Override
    public void onResume() {
        super.onResume();
        filterRemindersForSelectedDate();
        if (adView != null) adView.resume();
        AnalyticsHelper.logScreenView("Reminders", "RemindersFragment");
    }

    @Override
    public void onPause() {
        if (adView != null) adView.pause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (adView != null) adView.destroy();
        super.onDestroyView();
    }

    /* ---------------- MVVM OBSERVERS ---------------- */

    private void setupObservers() {
        // Performance: Single observer for all reminders, filtering done locally
        reminderViewModel.getAllReminders(currentUserId).observe(getViewLifecycleOwner(), reminders -> {
            allRemindersList = (reminders != null) ? reminders : new ArrayList<>();
            filterRemindersForSelectedDate();
            updateCalendarDots();
            
            // Update create button alpha based on total limit
            if (!isPremium && allRemindersList.size() >= FREE_REMINDER_LIMIT) {
                View createBtn = getView() != null ? getView().findViewById(R.id.createButton) : null;
                if (createBtn != null) createBtn.setAlpha(0.5f);
            } else {
                View createBtn = getView() != null ? getView().findViewById(R.id.createButton) : null;
                if (createBtn != null) createBtn.setAlpha(1.0f);
            }
        });
    }

    private void setupTimeline(List<Task> tasks) {
        TimelineAdapter adapter = new TimelineAdapter(
                requireContext(),
                tasks,
                this::showReminderOptions
        );

        hourRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        hourRecyclerView.setNestedScrollingEnabled(true);
        hourRecyclerView.setAdapter(adapter);
        
        scrollToActiveHour();
    }

    private void scrollToActiveHour() {
        if (hourRecyclerView == null) return;

        int targetHour = -1;

        // Priority 1: Current hour if today is selected
        if (selectedDate.equals(LocalDate.now())) {
            targetHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        } else if (!currentReminders.isEmpty()) {
            // Priority 2: First task hour of the day
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(currentReminders.get(0).notificationTime);
            targetHour = cal.get(Calendar.HOUR_OF_DAY);
        }

        if (targetHour != -1) {
            int finalTargetHour = targetHour;
            hourRecyclerView.post(() -> {
                LinearLayoutManager lm = (LinearLayoutManager) hourRecyclerView.getLayoutManager();
                if (lm != null) {
                    lm.scrollToPositionWithOffset(finalTargetHour, 0);
                }
            });
        }
    }

    private void filterRemindersForSelectedDate() {
        currentReminders = new ArrayList<>();
        for (ReminderEntity reminder : allRemindersList) {
            if (isReminderVisibleOnDate(reminder, selectedDate)) {
                currentReminders.add(reminder);
            }
        }
        
        // Sort currentReminders by time
        currentReminders.sort(Comparator.comparingLong(o -> o.notificationTime));
        
        updateUIWithReminders();
    }

    private boolean isReminderVisibleOnDate(ReminderEntity reminder, LocalDate date) {
        if (reminder.notificationTime == 0 || reminder.isDeleted) return false;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(reminder.notificationTime);
        LocalDate startDate = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

        // If it's a future reminder (not yet started), only show on its start date or later if repeated
        if (startDate.isAfter(date)) return false;

        // If it's the exact same day
        if (startDate.equals(date)) return true;

        // If it's in the past and not repeated, don't show
        if (!reminder.isRepeated || reminder.repeatType == null) return false;

        String rt = reminder.repeatType.trim().toLowerCase(Locale.ROOT);

        return switch (rt) {
            case "daily" -> true;
            case "weekly" -> startDate.getDayOfWeek() == date.getDayOfWeek();
            case "monthly" -> startDate.getDayOfMonth() == date.getDayOfMonth();
            case "yearly" ->
                    startDate.getMonth() == date.getMonth() && startDate.getDayOfMonth() == date.getDayOfMonth();
            default -> false;
        };
    }

    private void updateCalendarDots() {
        if (calendarAdapter == null) return;

        List<LocalDate> eventDates = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(10); 
        LocalDate end = start.plusDays(100); 

        for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) {
            for (ReminderEntity reminder : allRemindersList) {
                if (isReminderVisibleOnDate(reminder, d)) {
                    eventDates.add(d);
                    break; 
                }
            }
        }
        calendarAdapter.setEventDates(eventDates);
    }

    private void updateUIWithReminders() {
        List<Task> tasks = new ArrayList<>();

        for (ReminderEntity r : currentReminders) {
            String title = r.title;
            if (title == null || title.isEmpty()) title = r.message;

            Task t = new Task(
                    title,
                    r.type,
                    r.notificationTime,
                    r.id,
                    r.gradientStartColor,
                    r.gradientEndColor
            );
            t.setDone(r.isDone); // 👈 We need to update Task model too

            tasks.add(t);
        }

        updatePromptText();
        setupTimeline(tasks);
    }

    private void updatePromptText() {
        int count = currentReminders.size();
        if (count == 0) {
            promptTextView.setVisibility(View.GONE);
            hourRecyclerView.setVisibility(View.GONE);
            emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            promptTextView.setVisibility(View.VISIBLE);
            hourRecyclerView.setVisibility(View.VISIBLE);
            emptyStateLayout.setVisibility(View.GONE);
            promptTextView.setText(String.format(Locale.US," %d reminders are planned for this day.", count));
        }
    }

    /* ---------------- REMINDER OPTIONS ---------------- */

    private void showReminderOptions(Task task) {
        ReminderEntity reminder =
                currentReminders.stream().filter(r -> Objects.equals(r.id, task.getId())).findFirst().orElse(null);

        if (reminder == null) return;

        ReminderOptionsBottomSheet bottomSheet = new ReminderOptionsBottomSheet(reminder, new ReminderOptionsBottomSheet.ActionListener() {
            @Override
            public void onEdit(ReminderEntity reminder) {
                Intent i = new Intent(requireContext(), EditReminderActivity.class);
                i.putExtra(EXTRA_REMINDER_ID, reminder.id);
                startActivity(i);
            }

            @Override
            public void onDelete(ReminderEntity reminder) {
                deleteReminder(reminder);
            }
        });
        bottomSheet.show(getChildFragmentManager(), "ReminderOptions");
    }

    private void deleteReminder(ReminderEntity reminder) {
        reminderViewModel.deleteReminder(reminder);
        // UI will automatically update due to LiveData observation in loadRemindersForSelectedDate
    }

    /* ---------------- PICK DATE ---------------- */

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                new ContextThemeWrapper(requireContext(), R.style.CustomTimePickerTheme),
                (view, y, m, d) -> {
                    selectedDate = LocalDate.of(y, m + 1, d);
                    updateSelectedDateText();
                    loadMonthData();
                    filterRemindersForSelectedDate();
                },
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth()
        );
        dialog.show();
    }

    private void updateSelectedDateText() {
        selectedDateTv.setText(selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
    }

    /* ---------------- CALENDAR ---------------- */

    private void setupCalendar() {
        calendarRv.setLayoutManager(new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        calendarAdapter = new CalendarAdapter(calendarItemList);
        calendarRv.setAdapter(calendarAdapter);

        calendarAdapter.setOnDateClickListener((pos, item) -> {
            selectedDate = item.localDate;
            updateSelectedDateText();
            calendarAdapter.setSelectedPosition(pos);
            calendarRv.smoothScrollToPosition(pos);
            filterRemindersForSelectedDate();
        });

        // Load next month when scrolled to end
        calendarRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;

                int lastVisible = lm.findLastVisibleItemPosition();
                int total = calendarAdapter.getItemCount();

                if (lastVisible == total - 1) {
                    recyclerView.post(() -> appendNextMonth());
                }
            }
        });
    }

    private void loadMonthData() {
        calendarItemList.clear();
        YearMonth ym = YearMonth.from(selectedDate);
        LocalDate first = ym.atDay(1);
        int days = ym.lengthOfMonth();

        for (int i = 0; i < days; i++) {
            LocalDate date = first.plusDays(i);
            calendarItemList.add(new CalendarItem(
                    String.valueOf(date.getDayOfMonth()),
                    date.getDayOfWeek().name().substring(0, 3),
                    date
            ));
        }

        // Set selected position based on initial selectedDate
        int initialPos = -1;
        for (int i = 0; i < calendarItemList.size(); i++) {
            if (calendarItemList.get(i).localDate.equals(selectedDate)) {
                initialPos = i;
                break;
            }
        }
        if (initialPos != -1) {
            calendarAdapter.setSelectedPosition(initialPos);
            calendarRv.scrollToPosition(initialPos);
        }

        calendarAdapter.notifyDataSetChanged();
    }

    private void appendNextMonth() {
        if (calendarRv != null && calendarRv.isComputingLayout()) {
            calendarRv.post(this::appendNextMonthInternal);
        } else {
            appendNextMonthInternal();
        }
    }

    private void appendNextMonthInternal() {
        YearMonth currentMonth = YearMonth.from(selectedDate);
        YearMonth nextMonth = currentMonth.plusMonths(1);

        List<CalendarItem> nextItems = new ArrayList<>();
        LocalDate first = nextMonth.atDay(1);
        int days = nextMonth.lengthOfMonth();

        for (int i = 0; i < days; i++) {
            LocalDate date = first.plusDays(i);
            nextItems.add(new CalendarItem(
                    String.valueOf(date.getDayOfMonth()),
                    date.getDayOfWeek().name().substring(0, 3),
                    date
            ));
        }

        calendarAdapter.addNext(nextItems);
    }
}

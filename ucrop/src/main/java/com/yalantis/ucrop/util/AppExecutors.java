package com.yalantis.ucrop.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppExecutors {

    private static final int THREAD_COUNT = 5;
    private static final Object LOCK = new Object();

    private static AppExecutors instance;

    private final MainThreadExecutor mainThread;
    private final ExecutorService tasksThread;

    private AppExecutors(ExecutorService tasksThread,
                         MainThreadExecutor mainThread) {
        this.tasksThread = tasksThread;
        this.mainThread = mainThread;
    }

    public static AppExecutors getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new AppExecutors(Executors.newFixedThreadPool(THREAD_COUNT),
                                                new MainThreadExecutor());
                }
            }
        }
        return instance;
    }


    public ExecutorService tasksThread() {
        return tasksThread;
    }

    public MainThreadExecutor mainThread() {
        return mainThread;
    }

    public static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }

        public void execute(final Runnable command, final int delay) {
            mainThreadHandler.postDelayed(command, delay);
        }

        public void cancel(@NonNull Runnable command) {
            mainThreadHandler.removeCallbacks(command);
        }
    }
}


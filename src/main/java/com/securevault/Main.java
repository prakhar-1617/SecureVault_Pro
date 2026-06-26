package com.securevault;

import com.securevault.ui.MainApp;

/**
 * Bootstrap class to launch the JavaFX application.
 * Prevents "Error: JavaFX runtime components are missing" at runtime
 * when launched from packaged JARs.
 */
public class Main {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}

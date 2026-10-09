package com.ali.hafiflet;

import android.os.Bundle;

interface IShellService {
    // Shizuku'nun servisi kapatmak için kullandığı sabit işlem kodu.
    void destroy() = 16777114;

    // Komutu "sh -c" ile çalıştırır; "code" (int) ve "out" (String) döner.
    Bundle exec(String command) = 1;
}

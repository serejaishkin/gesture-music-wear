package com.example.gesturemusicwear;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class GestureTileService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

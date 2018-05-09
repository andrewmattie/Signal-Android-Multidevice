package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.qr.QrCode;
import org.thoughtcrime.securesms.service.LinkingService;

public class LinkingProgressActivity extends Activity {
  private ServiceConnection serviceConnection = new LinkingServiceConnection();
  private LinkingService linkingService;
  private View container;
  private ImageView qrCode;
  private EditText deviceNameEditText;
  private CircularProgressButton nextButton;
  private TextView titleText;

  private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.d("LinkingProgressActivity", "### " + intent);
      switch (intent.getAction()) {
        case LinkingService.LINKING_EVENT:
          shutdownService();
          startActivity(new Intent(LinkingProgressActivity.this, ConversationListActivity.class));
          finish();
          break;
        case LinkingService.LINKING_PUBKEY:
          Log.d("LinkingProgressActivity", "recv pubkey " + intent.getStringExtra(LinkingService.LINKING_PUBKEY));
          Bitmap qrCodeBitmap = QrCode.create(intent.getStringExtra(LinkingService.LINKING_PUBKEY));
          qrCode.setImageBitmap(qrCodeBitmap);
          break;
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d("LinkingProgressActivity", "onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.linking_progress_activity);

    qrCode = (ImageView) findViewById(R.id.linking_qr_code);
    deviceNameEditText = (EditText) findViewById(R.id.linking_device_name);
    titleText = (TextView) findViewById(R.id.linking_title);
    nextButton = (CircularProgressButton) findViewById(R.id.linking_next);

    deviceNameEditText.setText(android.os.Build.MODEL);
    nextButton.setOnClickListener(v -> generateQr());

    LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter(LinkingService.LINKING_EVENT));
    LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter(LinkingService.LINKING_PUBKEY));
  }

  private void generateQr() {
    titleText.setText("Scan the QR code below on your phone to link your Signal account.");
    nextButton.setVisibility(View.GONE);
    deviceNameEditText.setVisibility(View.GONE);
    Intent intent = new Intent(this, LinkingService.class);
    intent.putExtra("device_name", deviceNameEditText.getText().toString());
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    startService(intent);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    shutdownService();
  }

  private void shutdownServiceBinding() {
    if (serviceConnection != null) {
      unbindService(serviceConnection);
      serviceConnection = null;
    }
  }

  private void shutdownService() {
    if (linkingService != null) {
      linkingService.shutdown();
      linkingService = null;
    }

    shutdownServiceBinding();
    Intent serviceIntent = new Intent(this, LinkingService.class);
    stopService(serviceIntent);
  }

  private class LinkingServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
  }
}

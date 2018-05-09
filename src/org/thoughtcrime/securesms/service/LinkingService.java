package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.jobs.GcmRefreshJob;
import org.thoughtcrime.securesms.jobs.GroupSyncRequestJob;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.internal.util.Base64;

import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Benni on 08.07.2016.
 */
public class LinkingService extends Service {

  private final Binder binder = new LinkingServiceBinder();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  public static final String LINKING_EVENT = "org.thoughtcrime.securesms.LINKING_EVENT";
  public static final String LINKING_PUBKEY = "org.thoughtcrime.securesms.LINKING_PUBKEY";
  private String deviceNameExtra;
  private String password;
  private String temporarySignalingKey;
  private SignalServiceAccountManager accountManager;
  private IdentityKeyPair temporaryIdentity;
  private Intent intent;
  private SignalServiceAccountManager.NewDeviceRegistrationReturn ret;
  private int registrationId;
  private String gcmRegistrationId;
  private GroupSyncRequestJob groupSyncRequestJob;
  private IdentityKeyPair retIdentity;
  private String username;

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    Log.d("LinkingService", "onStartCommand");
    executor.execute(new Runnable() {
      @Override
      public void run() {
        handleLinkIntent(intent);
      }
    });
    return START_NOT_STICKY;
  }

  private void handleLinkIntent(final Intent linkIntent) {
    try {
      deviceNameExtra = linkIntent.getStringExtra("device_name");

      createTsDeviceLink();
      finishLink();
      saveIdentity();
      createPreKeys();
      savePublicKeyToDB();

      ApplicationContext.getInstance(this).getJobManager().add(groupSyncRequestJob);

      intent = new Intent(LINKING_EVENT);
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    } catch (Exception e) {
      Log.w("LinkingService", e);
    }
  }

  private void createTsDeviceLink() {
    try {
      password = Util.getSecret(18);
      temporaryIdentity = KeyHelper.generateIdentityKeyPair();
      accountManager = new SignalServiceAccountManager(new SignalServiceNetworkAccess(this).getConfiguration(this), null, password, BuildConfig.USER_AGENT);

      String uuid = accountManager.getNewDeviceUuid(); /* timeouts sometimes */
      URI tsdevicelink = new URI("tsdevice:/?uuid=" + URLEncoder.encode(uuid, "utf-8") + "&pub_key=" + URLEncoder.encode(Base64.encodeBytesWithoutPadding(temporaryIdentity.getPublicKey().serialize()), "utf-8"));
      Log.d("LinkingService", tsdevicelink.toString());
      intent = new Intent(LINKING_PUBKEY);
      intent.putExtra(LINKING_PUBKEY, tsdevicelink.toString());
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    } catch(Exception e) {
      Log.w("LinkingService", e);
    }
  }

  private void finishLink() {
    try {
      temporarySignalingKey = Util.getSecret(52);
      registrationId = KeyHelper.generateRegistrationId(false);
      String deviceName = deviceNameExtra;
      ret = accountManager.finishNewDeviceRegistration(temporaryIdentity, temporarySignalingKey, false, true, registrationId, deviceName);
      gcmRegistrationId = GoogleCloudMessaging.getInstance(this).register(GcmRefreshJob.REGISTRATION_ID);
      accountManager.setGcmId(Optional.of(gcmRegistrationId));

      groupSyncRequestJob = new GroupSyncRequestJob(this);
    } catch (Exception e) {
      Log.w("LinkingService", e);
    }
  }

  private void saveIdentity() {
    TextSecurePreferences.setDeviceId(this, ret.getDeviceId());
    username = ret.getNumber();
    TextSecurePreferences.setLocalNumber(this, ret.getNumber());
    retIdentity = ret.getIdentity();
    IdentityKeyUtil.setIdentityKeys(this, retIdentity);
  }

  private void createPreKeys() {
    try {
      List<PreKeyRecord> oneTimePreKeys = PreKeyUtil.generatePreKeys(this);
      SignedPreKeyRecord signedPreKey = PreKeyUtil.generateSignedPreKey(this, retIdentity, true);
      accountManager.setPreKeys(retIdentity.getPublicKey(), signedPreKey, oneTimePreKeys);
    } catch(Exception e) {
      Log.w("LinkingService", e);
    }
  }

  private void savePublicKeyToDB() {
    Recipient self = Recipient.from(this, Address.fromExternal(this, username), false);
    DatabaseFactory.getIdentityDatabase(this).saveIdentity(self.getAddress(), ret.getIdentity().getPublicKey(), IdentityDatabase.VerifiedStatus.VERIFIED, true, 0, true);

    TextSecurePreferences.setVerifying(this, false);
    TextSecurePreferences.setPushRegistered(this, true);
    TextSecurePreferences.setPushServerPassword(this, password);
    TextSecurePreferences.setSignalingKey(this, temporarySignalingKey);
    TextSecurePreferences.setSignedPreKeyRegistered(this, true);
    TextSecurePreferences.setPromptedPushRegistration(this, true);
    TextSecurePreferences.setWebsocketRegistered(this, true);
    TextSecurePreferences.setGcmRegistrationId(this, gcmRegistrationId);
    TextSecurePreferences.setMultiDevice(this, true);
    TextSecurePreferences.setLocalRegistrationId(this, registrationId);
    DirectoryRefreshListener.schedule(this);
    RotateSignedPreKeyListener.schedule(this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    executor.shutdown();
    shutdown();
  }

  public void shutdown() {
  }

  public class LinkingServiceBinder extends Binder {
    public LinkingService getService() {
      return LinkingService.this;
    }
  }
}

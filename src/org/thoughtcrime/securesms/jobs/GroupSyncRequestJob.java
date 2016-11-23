package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.inject.Inject;

/**
 * Created by Benni on 12.07.2016.
 */
public class GroupSyncRequestJob extends MasterSecretJob implements InjectableType {
    @Inject transient SignalServiceMessageSender messageSender;
    SignalServiceProtos.SyncMessage.Request groupSyncRequest;

    public GroupSyncRequestJob(Context context) {
        super(context, JobParameters.newBuilder()
                .withRequirement(new NetworkRequirement(context))
                .withRequirement(new MasterSecretRequirement(context))
                .withPersistence()
                .create());
        groupSyncRequest = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
    }


    @Override
    public void onRun(MasterSecret masterSecret) throws Exception {
        SignalServiceSyncMessage groupSyncRequestMessage = SignalServiceSyncMessage.forRequest(new RequestMessage(groupSyncRequest));

        try {
            messageSender.sendMessage(groupSyncRequestMessage);
        } catch(Exception e) {
            Log.w("GroupSyncRequestJob", e);
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        return false;
    }

    @Override
    public void onAdded() {

    }

    @Override
    public void onCanceled() {

    }
}

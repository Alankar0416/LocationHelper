package com.ixigo.lib.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

public class LocationHelper implements LocationListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    protected static final String TAG = LocationHelper.class.getSimpleName();

    private static final int UPDATE_INTERVAL_IN_MILLISECONDS = 5 * 1000;
    private static final int LOCATION_EXPIRY_MILLISECONDS = 5 * 60 * 1000; // 5 minutes
    private static final int REQUEST_LOCATION_SETTING_RESOLUTION = 10;
    private static final int REQUEST_SHOW_ERROR_DIALOG = 11;
    private static final int REQUEST_RESOLUTION_GOOGLE_API_CLIENT_CONNECTION_ERROR = 12;

    private static Location lastLocation;

    private Context mContext;
    private Handler mHandler;
    private Callbacks callbacks;
    private GoogleApiClient mGoogleApiClient;
    private boolean resolveOnError;
    private boolean forceNewLocation;
    private boolean resultDelivered;

    private LocationHelper(Context context) {
        mContext = context;

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mHandler = new Handler();
    }

    public static LocationHelper getInstance(Context context) {
        return new LocationHelper(context);
    }

    private void connectGoogleApiClient(Context context) {

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addOnConnectionFailedListener(this)
                    .addConnectionCallbacks(this)
                    .addApi(LocationServices.API).build();
        }

        if (!mGoogleApiClient.isConnecting() && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    public void prefetchLocation() {
        requestLocation(null);
    }

    public Location getLastLocation() {

        if (lastLocation == null) {
            requestLocation(null);
        }

        return lastLocation;
    }

    public void requestLocation(final Callbacks callbacks) {
        requestLocation(false, false, callbacks);
    }

    public void requestLocation(boolean resolveOnError, boolean forceNewLocation, Callbacks callbacks) {
        if (locationPermissionDenied()) {
            if (callbacks != null) {
                callbacks.onError();
            }
            return;
        }

        this.resultDelivered = false;
        this.callbacks = callbacks;
        this.resolveOnError = resolveOnError;
        this.forceNewLocation = forceNewLocation;

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            if (forceNewLocation) {
                checkAndStartLocationUpdate();
            } else {

                Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if (lastLocation != null) {
                    LocationHelper.lastLocation = lastLocation;
                    mGoogleApiClient.disconnect();
                    sendLocation(lastLocation);
                } else {
                    checkAndStartLocationUpdate();
                }

            }
        } else {
            connectGoogleApiClient(mContext);
        }
    }

    private boolean locationPermissionDenied() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED || ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED;
    }

    private void sendError() {
        if (callbacks != null && !resultDelivered) {
            resultDelivered = true;
            callbacks.onError();
        }
    }

    private void sendLocation(Location location) {
        if (callbacks != null && !resultDelivered) {
            resultDelivered = true;
            callbacks.onLocationReceived(location);
        }
    }

    private void startLocationUpdate(LocationRequest mLocationRequest) {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

        final Runnable failureCallback = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Location Failed");

                mHandler.removeCallbacks(this);
                if (mGoogleApiClient.isConnected()) {
                    LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, LocationHelper.this);
                    mGoogleApiClient.disconnect();
                }
                sendError();
            }
        };
        mHandler.postDelayed(failureCallback, 10000);

        if (callbacks != null) {
            callbacks.onLocationRequested();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        lastLocation = location;
        mHandler.removeCallbacksAndMessages(null);
        mGoogleApiClient.disconnect();

        sendLocation(location);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (forceNewLocation || lastLocation == null) {
            checkAndStartLocationUpdate();
        } else {
            mGoogleApiClient.disconnect();
            sendLocation(lastLocation);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient connection failed: " + connectionResult.toString());
        if (resolveOnError && mContext instanceof Activity) {
            if (!connectionResult.hasResolution()) {
                // show the localized error dialog.
                GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
                apiAvailability.getErrorDialog((Activity) mContext, connectionResult.getErrorCode(), REQUEST_SHOW_ERROR_DIALOG).show();
            } else {
                try {
                    connectionResult.startResolutionForResult((Activity) mContext, REQUEST_RESOLUTION_GOOGLE_API_CLIENT_CONNECTION_ERROR);
                } catch (IntentSender.SendIntentException e) {
                    Log.e(TAG, "Exception while starting resolution activity", e);
                }
            }
        }
        sendError();
    }

    private void checkAndStartLocationUpdate() {
        final LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequest.setNumUpdates(1);
        mLocationRequest.setExpirationDuration(LOCATION_EXPIRY_MILLISECONDS);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);

        PendingResult<LocationSettingsResult> pendingResult = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        pendingResult.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        Log.i(TAG, "All location settings are satisfied. Start location updates");
                        startLocationUpdate(mLocationRequest);
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings.");

                        //Id resolveOnError flag is set we resolve it by user intervention.
                        if (resolveOnError) {
                            if (mContext instanceof Activity && !((Activity) mContext).isFinishing()) {

                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the result
                                    // in onActivityResult() and disconnect google API services from there
                                    //TODO setup an activity to handle ResolutionResult
                                    status.startResolutionForResult((Activity) mContext, REQUEST_LOCATION_SETTING_RESOLUTION);
                                } catch (IntentSender.SendIntentException e) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                            }

                            mGoogleApiClient.disconnect();

                            //TODO we are failing for now but in future will handle it in onActivityResult
                            sendError();
                        } else {
                            mGoogleApiClient.disconnect();

                            //This error cannot be recovered without user intervention
                            sendError();
                        }

                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
                        mGoogleApiClient.disconnect();
                        sendError();
                        break;
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        //Not handling suspensions right now
    }

    public interface Callbacks {

        void onLocationRequested();

        void onLocationReceived(Location location);

        void onError();

    }

}

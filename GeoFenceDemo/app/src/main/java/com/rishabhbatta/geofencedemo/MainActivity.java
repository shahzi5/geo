package com.rishabhbatta.geofencedemo;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.rishabhbatta.geofencedemo.db.GeofenceContract;
import com.rishabhbatta.geofencedemo.db.GeofenceStorage;

import java.util.ArrayList;
import java.util.List;

import static com.rishabhbatta.geofencedemo.R.id.map;
import static junit.runner.Version.id;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener, GoogleMap.OnInfoWindowClickListener, LocationListener {

    private static final String TAG = "MainActivity";
    public static final String SHARED_PREFERENCES_NAME = BuildConfig.APPLICATION_ID + ".SHARED_PREFERENCES_NAME";
    public static final String NEW_GEOFENCE_NUMBER = BuildConfig.APPLICATION_ID + ".NEW_GEOFENCE_NUMBER";
    public static final long GEOFENCE_EXPIRATION_IN_HOURS = 12;
    public static final long GEOFENCE_EXPIRATION_IN_MILLISECONDS = GEOFENCE_EXPIRATION_IN_HOURS * 60 * 1000;
    public static final float GEOFENCE_RADIUS_IN_METERS = 100; // 100 m
    private static final int PERMISSIONS_REQUEST = 105;
    private static final int PERMISSION_REQUEST_CODE = 110;

    protected GoogleApiClient mGoogleApiClient;
    protected ArrayList<Geofence> mGeofenceList;
    private PendingIntent mGeofencePendingIntent;
    private SharedPreferences mSharedPreferences;
    private GoogleMap googleMap;
    private Location lastLocation;
    LocationManager locationManager;
    String bestProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        mGeofenceList = new ArrayList<>();
        mGeofencePendingIntent = null;
        mSharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        buildGoogleApiClient();

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        bestProvider = locationManager.getBestProvider(criteria, true);
//                initMap(googleMap);


    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }


    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected to GoogleApiClient");
        getLastKnownLocation();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason.
        Log.i(TAG, "Connection suspended");

        // onConnected() will be called again automatically when the service reconnects
    }



    private GeofencingRequest getGeofencingRequest(Geofence geofence) {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofence(geofence);
        return builder.build();
    }

    private void logSecurityException(SecurityException securityException) {
        Log.e(TAG, "Invalid location permission. " +
                "You need to use ACCESS_FINE_LOCATION with geofences", securityException);
    }
    private PendingIntent getGeofencePendingIntent() {
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTrasitionService.class);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        if(!runtime_permission("uuu"))
            initMap(googleMap);
        runtime_permission("uuu");


    }

    private void reloadMapMarkers() {
        googleMap.clear();
        try (Cursor cursor = GeofenceStorage.getCursor()) {
            while (cursor.moveToNext()) {
                long expires = Long.parseLong(cursor.getString(cursor.getColumnIndex(GeofenceContract.GeofenceEntry.COLUMN_NAME_EXPIRES)));
                if(System.currentTimeMillis() < expires) {
                    String key = cursor.getString(cursor.getColumnIndex(GeofenceContract.GeofenceEntry.COLUMN_NAME_KEY));
                    double lat = Double.parseDouble(cursor.getString(cursor.getColumnIndex(GeofenceContract.GeofenceEntry.COLUMN_NAME_LAT)));
                    double lng = Double.parseDouble(cursor.getString(cursor.getColumnIndex(GeofenceContract.GeofenceEntry.COLUMN_NAME_LNG)));
                    addMarker(key, new LatLng(lat, lng));
                }
            }
        }
    }


    private void initMap(GoogleMap map) {

                Location location = locationManager.getLastKnownLocation(bestProvider);
                if (location != null) {
                    onLocationChanged(location);
                }
                locationManager.requestLocationUpdates(bestProvider, 20000, 0, this);
                map.setMyLocationEnabled(true);
                map.setOnMapClickListener(this);
                map.setOnMarkerClickListener(this);
                map.setOnInfoWindowClickListener(this);

                reloadMapMarkers();



    }

    private boolean runtime_permission(String get) {

        if(Build.VERSION.SDK_INT>=23 && ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED&& ContextCompat.checkSelfPermission(MainActivity.this,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){

            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},PERMISSIONS_REQUEST);
            if(get.equals("yoo")){
                initMap(googleMap);
            }
            return true;
        }

        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==PERMISSIONS_REQUEST){

            System.out.println("-------------- " + grantResults.length +" ");

            try{
                if(grantResults[0]==(PackageManager.PERMISSION_GRANTED )&& grantResults[1]==(PackageManager.PERMISSION_GRANTED)){
                    initMap(googleMap);
                }else{
                    runtime_permission("yoo");
                }
            }
            catch(Exception e){

            }
        }

    }

    @Override
    public void onMapClick(final LatLng latLng) {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, "Google Api not connected!", Toast.LENGTH_SHORT).show();
            return;
        }
        final String key = getNewGeofenceNumber() + "";
        final long expTime = System.currentTimeMillis() + GEOFENCE_EXPIRATION_IN_MILLISECONDS;
        addMarker(key, latLng);
        Geofence geofence = new Geofence.Builder()
                .setRequestId(key)
                .setCircularRegion(
                        latLng.latitude,
                        latLng.longitude,
                        GEOFENCE_RADIUS_IN_METERS
                )
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                        Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    getGeofencingRequest(geofence),
                    getGeofencePendingIntent()
            ).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    if (status.isSuccess()) {
                        GeofenceStorage.saveToDb(key, latLng, expTime);
                        Toast.makeText(MainActivity.this, "Geofence Added!", Toast.LENGTH_SHORT).show();
                    } else {
                        String errorMessage = GeofenceTrasitionService.getErrorString(MainActivity.this, status.getStatusCode());
                        Log.e(TAG, errorMessage);
                    }
                }
            });
        } catch (SecurityException securityException) {
            logSecurityException(securityException);
        }
    }

    private void addMarker(String key, LatLng latLng) {
        googleMap.addMarker(new MarkerOptions()
                .title("G:" + key)
                .snippet("Click here if you want delete this geofence")
                .position(latLng));
        googleMap.addCircle(new CircleOptions()
                .center(latLng)
                .radius(GEOFENCE_RADIUS_IN_METERS)
                .strokeColor(Color.RED)
                .fillColor(Color.parseColor("#80ff0000")));
    }

    private int getNewGeofenceNumber(){
        int number = mSharedPreferences.getInt(NEW_GEOFENCE_NUMBER, 0);
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putInt(NEW_GEOFENCE_NUMBER, number + 1);
        editor.commit();
        return number;
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }
    @Override
    public void onInfoWindowClick(Marker marker) {
        final String requestId = marker.getTitle().split(":")[1];
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, "GeoFence Not connected!", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            List<String> idList = new ArrayList<>();
            idList.add(requestId);
            LocationServices.GeofencingApi.removeGeofences(mGoogleApiClient, idList).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    if (status.isSuccess()) {
                        GeofenceStorage.removeGeofence(requestId);
                        Toast.makeText(MainActivity.this, "Geofence removed!", Toast.LENGTH_SHORT).show();
                        reloadMapMarkers();
                    } else {
                        // Get the status code for the error and log it using a user-friendly message.
                        String errorMessage = GeofenceTrasitionService.getErrorString(MainActivity.this,
                                status.getStatusCode());
                        Log.e(TAG, errorMessage);
                    }
                }
            });
        } catch (SecurityException securityException) {
            logSecurityException(securityException);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        writeActualLocation(location);

    }
    private void getLastKnownLocation() {
        //noinspection MissingPermission
        lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if ( lastLocation != null ) {
                Log.i(TAG, "LasKnown location. " +
                        "Long: " + lastLocation.getLongitude() +
                        " | Lat: " + lastLocation.getLatitude());
                writeLastLocation();
//                startLocationUpdates();
            } else {
                Log.w(TAG, "No location retrieved yet");
//                startLocationUpdates();
            }
        }



    private Marker locationMarker;
    // Create a Location Marker

    private void writeLastLocation() {
        writeActualLocation(lastLocation);
    }
    private void writeActualLocation(Location location) {
        // ...
        markerLocation(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    private void markerLocation(LatLng latLng) {
        Log.i(TAG, "markerLocation("+latLng+")");
        String title = latLng.latitude + ", " + latLng.longitude;
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(title);
        if ( googleMap!=null ) {
            // Remove the anterior marker
            if ( locationMarker != null )
                locationMarker.remove();
            locationMarker = googleMap.addMarker(markerOptions);
            float zoom = 14f;
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
            googleMap.animateCamera(cameraUpdate);
        }
    }
    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }



}

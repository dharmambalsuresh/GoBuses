package com.example.mapactivity;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.transit.realtime.GtfsRealtime;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    LocationManager locationManager;
    SharedPreferences sharedPreferences;
    LatLng ne = null;
    LatLng currentlocation;
    Map<String, Marker> busMarkers = new HashMap<String, Marker>();
    Marker myMarker;
    public List<GtfsRealtime.FeedEntity> buseslocation = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, new LocationListener() {
                @Override
                //updating the marker to the current location
                public void onLocationChanged(Location location) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    currentlocation = new LatLng(latitude, longitude);
                    Geocoder geocoder = new Geocoder(getApplicationContext());
                    try {
                        List<Address> addressList = geocoder.getFromLocation(latitude, longitude, 1);
                        String str = addressList.get(0).getLocality();
                        str += addressList.get(0).getCountryName();


                        if (myMarker == null) {
                            myMarker = mMap.addMarker(new MarkerOptions().position(currentlocation).title(str));
                        } else {
                            myMarker.setPosition(currentlocation);
                        }
                        //mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

                    } catch (IOException e) {
                        e.printStackTrace();
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
            });
        } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new LocationListener() {
                //updating the marker to the current location
                @Override
                public void onLocationChanged(Location location) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    currentlocation = new LatLng(latitude, longitude);
                    Geocoder geocoder = new Geocoder(getApplicationContext());

                    try {
                        List<Address> addressList = geocoder.getFromLocation(latitude, longitude, 1);
                        String str = addressList.get(0).getLocality();
                        str += addressList.get(0).getCountryName();
                        if (myMarker == null) {
                            myMarker = mMap.addMarker(new MarkerOptions().position(currentlocation).title(str));

                        } else {
                            myMarker.setPosition(currentlocation);

                        }


                    } catch (IOException e) {
                        e.printStackTrace();
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
            });
        }
        //for updating the map every 15 seconds
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                new buslocations().execute();
            }
        }, 0, 15000);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    public LatLng GetLatLng(String data) {
        String[] latlong = data.split(",");
        if (latlong.length != 2) return null;
        double latitude = Double.parseDouble(latlong[0]);
        double longitude = Double.parseDouble(latlong[1]);
        return new LatLng(latitude, longitude);
    }

    public class buslocations extends AsyncTask<Void, Void, List<GtfsRealtime.FeedEntity>> {
        @Override
        protected List<GtfsRealtime.FeedEntity> doInBackground(Void... voids) {
            URL url = null;
            try {
                url = new URL("http://gtfs.halifax.ca/realtime/Vehicle/VehiclePositions.pb");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            GtfsRealtime.FeedMessage feed = null;
            try {
                feed = GtfsRealtime.FeedMessage.parseFrom(url.openStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            String bus_info = "";
            buseslocation.clear();
            for (GtfsRealtime.FeedEntity buses : feed.getEntityList()) {
                buseslocation.add(buses);
            }
            return buseslocation;
        }

        @Override
        protected void onPostExecute(List<GtfsRealtime.FeedEntity> buseslocation) {
            //retrieving the bus location and details
            for (GtfsRealtime.FeedEntity buses : buseslocation) {
                String id = buses.getVehicle().getTrip().getRouteId();
                double latitude = buses.getVehicle().getPosition().getLatitude();
                double longitude = buses.getVehicle().getPosition().getLongitude();
                LatLng latLng = new LatLng(latitude, longitude);

                //for customizing the marker icon
                int height=70;
                int width=70;
                BitmapDrawable bitmapDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.bus, null);
                Bitmap bitmap = bitmapDrawable.getBitmap();
                Bitmap busicon = Bitmap.createScaledBitmap(bitmap, width, height, false);

                //bus markers are the hasmap which has the location details of the buses
                if (busMarkers.containsKey(id)) {
                    Marker res = busMarkers.get(id);
                    res.setPosition(latLng);
                } else {
                    Marker res=mMap.addMarker(new MarkerOptions().position(latLng).title(id).icon(BitmapDescriptorFactory.fromBitmap(busicon)));
                    busMarkers.put(id, res);
                }
            }
        }
    }

    LatLng lastBounds;

    @Override
    public void onMapReady(GoogleMap googleMap) {

        //for initializing the google map into the map that we created
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        //button for getting back to the current location
        mMap.setMyLocationEnabled(true);


        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                //for restoring the last instance of map
                SharedPreferences data = getSharedPreferences("lastBound", MODE_PRIVATE);
                if (data.getBoolean("LatLng", false)) {
                    LatLng farLeft = GetLatLng(data.getString("farLeft", ""));
                    LatLng farRight= GetLatLng(data.getString("farRight",""));
                    LatLng nearLeft= GetLatLng(data.getString("nearLeft",""));
                    LatLng nearRight=GetLatLng(data.getString("nearRight",""));

                    LatLngBounds.Builder bndBuild = new LatLngBounds.Builder();
                    bndBuild.include(farLeft);
                    bndBuild.include(farRight);
                    bndBuild.include(nearLeft);
                    bndBuild.include(nearRight);
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bndBuild.build(), 200));

                }
                else{

                }
            }
        });

        Runnable rb = new Runnable() {
            @Override
            public void run() {

            }
        };
        Handler hs = new Handler();
        hs.postDelayed(rb, 2000);
        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove()
            {
                //for storing the last instance of map
                VisibleRegion vsr = mMap.getProjection().getVisibleRegion();
                SharedPreferences.Editor editor = getSharedPreferences("lastBound", MODE_PRIVATE).edit();
                String farLeft = vsr.farLeft.latitude+","+vsr.farLeft.longitude;
                String farRight = vsr.farRight.latitude+","+vsr.farRight.longitude;
                String nearLeft = vsr.nearLeft.latitude+","+vsr.nearLeft.longitude;
                String nearRight= vsr.nearRight.latitude+","+vsr.nearRight.longitude;
                editor.putString("farLeft", farLeft);
                editor.putString("farRight",farRight);
                editor.putString("nearLeft",nearLeft);
                editor.putString("nearRight",nearRight);
                editor.putBoolean("LatLng", true);
                editor.apply();
            }
        });
    }



}

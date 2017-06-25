# LocationHelper
A Simple Location Helper which saves you from all the heavy lifting while requesting a location in Andorid. 
It is customizable as per the requirement.

Sample usage : 

arg0 - resolveOnError //Wether you need to let user intervene in case of any error. Should be false if you are getting location in the background.
arg1 - forceNewLocation // If you want a fresh location right now ONLY


     LocationHelper.getInstance(context).requestLocation(true, true, new LocationHelper.Callbacks() {                               

                                        @Override
                                        public void onLocationRequested() {
                                        //Location update is being requested 
                                        //You need to probably show a loader here
                                        // only when resolveOnError is true
                                        }
                                        
                                        @Override
                                        public void onLocationReceived(Location location) {}
                                        //The requested Location
                                        }

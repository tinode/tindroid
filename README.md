# Demo chat for Android

Work in progress. What works:

* Send and receive messages
* In-app presence notifications
* Push notifications
* Unread message counters
* Offline mode is semi-functional
* Indicators for messages received/read (little check marks in messages)
* Register new account, login
* Load the list of contacts and store it offline
* Access contacts from the Android stock Contacts app
* Invite contacts to the app by SMS or email

Does not work yet:

* Editing an existing account
* Contact discovery - can't find other people to chat with
* Deleting/muting topics and messages
* Can't start new group conversations
* No client support for typing notifications.
* No encryption

Dependencies on the SDK side:

* [jackson](https://github.com/FasterXML/jackson) for json serialization
* [nv-websocket-client](https://github.com/TakahikoKawasaki/nv-websocket-client) for
websocket support

Dependencies on the application side:

* [libphonenumber](https://github.com/googlei18n/libphonenumber) for user discovery
to ensure all phone numbers use the same [E.164 format](https://en.wikipedia.org/wiki/E.164)
* [google-services](https://firebase.google.com/docs/cloud-messaging/android/client) for push notifications.
In order to compile the app you need to [generate your own](https://developers.google.com/mobile/add)
config file `google-services.json`. Once downloaded, copy it to the `./app/` folder. The
config file contains Google-provided passwords and as such cannot be shared. If you don't do it the 
app will crash with non-obvious exceptions. The Google-provided server key must be copied to `tinode.conf`, see 
details [here](https://github.com/tinode/chat). 

The `contacts.vcf` contains a list of contacts which can be used for testing. Push it to your emulator using

  `adb push contacts.vcf /sdcard/contacts.vcf`

<img src="android-contacts.png" alt="App screenshot - contacts" width="270" />
<img src="android-messages.png" alt="App screenshot - contacts" width="270" />

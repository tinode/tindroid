# Demo chat for Android

Work in progress. What works:

* Send and receive message
* Presence notifications
* Unread message counters
* Indicators for messages received/read (little check marks in messages)
* Register new account, login
* Load the list of contacts and store it offline
* Access contacts from the Android stock Contacts app
* Invite contacts to the app by SMS or email

Does not work yet:

* Editing an existing account
* Contact discovery - can't find other people to chat with
* Deleting/muting topics and messages
* Messages are not stored offline
* Can't start new conversations
* No push notifications
* Typing notifications

Dependencies on the SDK side:

* [jackson](https://github.com/FasterXML/jackson) for json serialization
* [nv-websocket-client](https://github.com/TakahikoKawasaki/nv-websocket-client) for websocket support

Dependencies on the application side:

* [libphonenumber](https://github.com/googlei18n/libphonenumber) for user discovery, making sure all phone numbers use the same E.164 format

The `contacts.vcf` contains a list of contacts which can be used for testing. Push it to your emulator using

  `adb push contacts.vcf /sdcard/contacts.vcf`

<img src="android-contacts.png" alt="App screenshot - contacts" width="270" />
<img src="android-messages.png" alt="App screenshot - contacts" width="270" />

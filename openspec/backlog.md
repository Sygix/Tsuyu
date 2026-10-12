# Backlog: undecided topics

None of these topics is specified or decided. Each one will become an OpenSpec change (`openspec/changes/<id>/`) or be attached to an existing change when it is addressed. The leads below are working assumptions, not decisions.

## 1. TVs whose adbd does not listen on TCP

**Observation:** on the reference TV, the firmware sets adbd's TCP port at every boot. A TV that does not do this only offers wireless debugging over TLS, and pairing it from the TV itself is impossible.

**Leads:**
- a one-time pairing from a computer or a phone, with a key Tsuyu can use afterwards;
- an accessibility service that reads the code and the port on the pairing screen, without taking focus.

**To decide:** whether these TVs are supported, in which phase, and which lead is chosen.

## 2. Enabling USB debugging

**Observation:** USB debugging survives a reboot, unlike wireless debugging. While it is on, adbd's TCP port stays open on the local network; only authorized keys get in.

**To decide:** what Tsuyu asks the user to enable, what it explains about this exposure, and whether it offers to close it once the server is running.

## 3. API version reported to clients

**Observation:** Shizuku-API is at version 13.1.5; Shevery reports 14.3.

**To decide:** the version Tsuyu reports, and which AIDL methods it really implements (UserService, remote process, etc.), after checking what SygixOs and the demo app use.

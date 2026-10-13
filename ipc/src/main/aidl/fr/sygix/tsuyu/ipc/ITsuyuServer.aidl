/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ipc;
interface ITsuyuServer {
    int getUid();
    boolean isReady();
    void exit();
    IBinder getClientService();
    Bundle getRequest(String token);
    void confirm(String token, boolean allowed);
    Bundle getAccess();
    void revoke(int uid);
    Bundle getPersistence();
    Bundle preparePersistence();
    Bundle disableKeyExpiry();
}

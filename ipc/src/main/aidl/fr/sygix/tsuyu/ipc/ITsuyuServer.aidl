/*
 * Copyright (C) 2026 Sygix
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package fr.sygix.tsuyu.ipc;
interface ITsuyuServer {
    int getUid();
    boolean isReady();
    void exit();
}

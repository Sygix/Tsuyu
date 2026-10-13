/*
 * Copyright (c) 2021 RikkaW
 * SPDX-License-Identifier: MIT
 */

package moe.shizuku.server;
interface IShizukuServiceConnection {
    oneway void connected(IBinder service);
    oneway void died();
}

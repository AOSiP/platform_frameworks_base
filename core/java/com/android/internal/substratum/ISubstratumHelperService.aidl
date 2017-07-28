package com.android.internal.substratum;

oneway interface ISubstratumHelperService {
    void applyBootAnimation();
    void applyShutdownAnimation();
    void applyProfile(in String name);
}

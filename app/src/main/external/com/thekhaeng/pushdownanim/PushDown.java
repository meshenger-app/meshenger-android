package com.thekhaeng.pushdownanim;

import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Created by The Khaeng on 21 Mar 2018 :)
 */

public interface PushDown{
    PushDown setScale( float scale );

    PushDown setScale( @PushDownAnim.Mode int mode, float scale );

    PushDown setDurationPush( long duration );

    PushDown setDurationRelease( long duration );

    PushDown setInterpolatorPush( AccelerateDecelerateInterpolator interpolatorPush );

    PushDown setInterpolatorRelease( AccelerateDecelerateInterpolator interpolatorRelease );

    PushDown setOnClickListener( View.OnClickListener clickListener );

    PushDown setOnLongClickListener( View.OnLongClickListener clickListener );

    PushDown setOnTouchEvent( View.OnTouchListener eventListener );
}

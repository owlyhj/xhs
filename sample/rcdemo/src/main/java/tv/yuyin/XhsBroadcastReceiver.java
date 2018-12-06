package tv.yuyin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * @author hjyu
 * @date 2018/12/4.
 * @see <a href="http://www.xfyun.cn">讯飞开放平台</a>
 */

public class XhsBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent it=new Intent(context,MainActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(it);
    }
}

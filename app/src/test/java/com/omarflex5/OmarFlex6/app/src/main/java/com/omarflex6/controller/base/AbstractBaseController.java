package com.omarflex6.controller.base;

import android.content.Intent;

public abstract class AbstractBaseController implements BaseController {

    @Override
    public void onAttach() {
        // Default no-op
    }

    @Override
    public void onDetach() {
        // Default no-op
    }

    @Override
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }
}

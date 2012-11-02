package com.liveramp.megadesk.curator;

import com.liveramp.megadesk.device.Device;
import com.liveramp.megadesk.device.Read;
import com.liveramp.megadesk.status.StringSerialization;
import com.liveramp.megadesk.status.check.EqualityStatusCheck;
import com.netflix.curator.framework.CuratorFramework;

public class StringDevice extends CuratorDevice<String> implements Device<String> {

  public StringDevice(CuratorFramework curator,
                      String id) throws Exception {
    super(curator, id, new StringSerialization());
  }

  public Read at(String status) {
    return new Read<String>(this, new EqualityStatusCheck<String>(status));
  }
}

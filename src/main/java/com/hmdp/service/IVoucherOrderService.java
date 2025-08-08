package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;



public interface IVoucherOrderService  extends IService<VoucherOrder> {


    public Result seckillVoucher(Long voucherId);

    public void  createVoucherOrder(VoucherOrder voucherId);
}

package myaccelerator

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.{LazyRoCC, LazyRoCCModuleImp, OpcodeSet}
import freechips.rocketchip.rocket.M_XRD
import freechips.rocketchip.rocket.M_XWR

class CustomAccelerator(opcodes: OpcodeSet)
    (implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new CustomAcceleratorModule(this)
}

object FunctionalUnitOpcode {
  val WRITE = 0.U
  val READ = 1.U
  val LOAD = 2.U
  val ADD = 3.U
}

class CustomAcceleratorModule(outer: CustomAccelerator)
    extends LazyRoCCModuleImp(outer) {
  

  val regs = Reg(Vec(32, UInt(64.W)))

  // Memory request state machine states
  val sIdle::sWaitMemLoadFire::sWaitMemStoreFire::sWaitMemLoadResp::sWaitMemStoreResp::sWaitCPUReceive::Nil = Enum(6)
  val state = RegInit(sIdle)
  
  val loadIdx = Reg(UInt(5.W))
  val loadAddr = Reg(UInt(64.W))
  val storeAddr = Reg(UInt(64.W))
  val storeData = Reg(UInt(64.W))
  val readData = Reg(UInt(64.W))
  val readRd = Reg(UInt(5.W))
  val tag = RegInit(0.U((io.mem.req.bits.tag.getWidth.W))) // tag for memory requests, if needed

  // Default values for the outputs
  io.busy := (state =/= sIdle)  
  io.interrupt := false.B
  
  io.mem.req.valid := (state === sWaitMemLoadFire) || (state === sWaitMemStoreFire)
  io.mem.req.bits.data := 0.U  
  io.mem.req.bits.cmd := 0.U
  io.mem.req.bits.size := 0.U
  io.mem.req.bits.addr := 0.U
  io.mem.req.bits.tag := 0.U
  io.mem.req.bits.phys := false.B
  io.mem.req.bits.dv := false.B
  io.mem.req.bits.dprv := 3.U
  io.mem.req.bits.signed := false.B
  io.mem.req.bits.dv := false.B  
  io.mem.req.bits.no_resp := false.B
  io.mem.req.bits.no_alloc := false.B
  io.mem.req.bits.no_xcpt := false.B
  io.mem.s1_kill := false.B
  io.mem.s2_kill := false.B

  val cmd = Queue(io.cmd)
  
  cmd.ready := (state === sIdle) // only accept new commands when idle  
  // The parts of the command are as follows
  // inst - the parts of the instruction itself
  //   opcode
  //   rd - destination register number
  //   rs1 - first source register number
  //   rs2 - second source register number
  //   funct
  //   xd - is the destination register being used?
  //   xs1 - is the first source register being used?
  //   xs2 - is the second source register being used?
  // rs1 - the value of source register 1
  // rs2 - the value of source register 2  

  io.resp.bits.rd := cmd.bits.inst.rd  
  io.resp.valid := {state === sWaitCPUReceive} // response is valid when waiting for CPU to receive it

  switch(state) {
    is(sIdle) {      
      when(cmd.fire) {
        switch(cmd.bits.inst.funct) {
          is(FunctionalUnitOpcode.WRITE) {
            regs(cmd.bits.rs2) := cmd.bits.rs1      
            printf(p"Write command: regs[${cmd.bits.rs2}] := 0x${Hexadecimal(cmd.bits.rs1)}\n")      
          }
          is(FunctionalUnitOpcode.READ) {            
            readData := regs(cmd.bits.rs2)                                    
            readRd := cmd.bits.inst.rd
            printf(p"Read command: regs[${cmd.bits.rs2}] = 0x${Hexadecimal(regs(cmd.bits.rs2))}\n")
            state := sWaitCPUReceive
          }
          is(FunctionalUnitOpcode.LOAD) {
            loadIdx := cmd.bits.rs2(4,0)
            loadAddr := cmd.bits.rs1         
            state := sWaitMemLoadFire            
            printf(p"Load command: load from address 0x${Hexadecimal(cmd.bits.rs1)} into regs[${cmd.bits.rs2}]\n")
          }
          is(FunctionalUnitOpcode.ADD) {
            regs(0) := cmd.bits.rs1 + regs(cmd.bits.rs2)                        
            printf(p"Add command: regs[0] := 0x${Hexadecimal(cmd.bits.rs1)} + 0x${Hexadecimal(regs(cmd.bits.rs2))}\n")
          }
        }
      }
    }
    is(sWaitMemLoadFire) {      
      io.mem.req.bits.addr := loadAddr
      io.mem.req.bits.cmd := M_XRD
      io.mem.req.bits.size := 3.U // 8 bytes      
      io.mem.req.bits.tag := tag
      io.mem.req.bits.mask := 0xFF.U // full 8 byte mask
      printf(p"Memory load request with tag ${tag}, addr=0x${Hexadecimal(loadAddr)}\n")
      printf(p"tag width = ${io.mem.req.bits.tag.getWidth.U}\n")
      when(io.mem.req.fire) {      
        state := sWaitMemLoadResp                
        tag := tag + 1.U // increment tag for next request        
        printf(p"Firing memory load request with tag ${tag}, addr=0x${Hexadecimal(loadAddr)}\n")
      } 
    }
    is(sWaitMemStoreFire) {         
      io.mem.req.bits.addr := storeAddr
      io.mem.req.bits.data := storeData
      io.mem.req.bits.cmd := M_XWR
      io.mem.req.bits.size := 3.U // 8 bytes      
      io.mem.req.bits.tag := tag      
      io.mem.req.bits.mask := 0xFF.U // full 8 byte mask
      when(io.mem.req.fire) {        
        state := sWaitMemStoreResp
        tag := tag + 1.U // increment tag for next request        
      }
    }
    is(sWaitMemLoadResp) {
      printf(p"nack: ${io.mem.resp.valid}, tag: ${io.mem.resp.bits.tag}, data: 0x${Hexadecimal(io.mem.resp.bits.data)}\n")
      printf(p"s2_xcpt = ${io.mem.s2_xcpt.asUInt}\n")
      printf(p"resp.valid=${io.mem.resp.valid}\n")
      printf(p"resp.tag=${io.mem.resp.bits.tag}\n")
      printf(p"resp.data=0x${Hexadecimal(io.mem.resp.bits.data)}\n")
      printf(p"nack=${io.mem.s2_nack}\n")
      when(io.mem.resp.valid) {
        regs(loadIdx) := io.mem.resp.bits.data
        state := sIdle                
        printf(p"RESP tag=${io.mem.resp.bits.tag} data=0x${Hexadecimal(io.mem.resp.bits.data)}\n")
      }
    }
    is(sWaitMemStoreResp) {      
      when(io.mem.resp.valid) {
        state := sIdle
      }
    }
    is(sWaitCPUReceive) {
      printf(p"Waiting for CPU to receive response...\n")
      io.resp.bits.data := readData // ensure response data is up to date with the register value
      io.resp.bits.rd := readRd
      when(io.resp.fire) {
        printf(p"CPU received response for read command, data=0x${Hexadecimal(io.resp.bits.data)}\n")        
        state := sIdle
      }
    }
  }
}
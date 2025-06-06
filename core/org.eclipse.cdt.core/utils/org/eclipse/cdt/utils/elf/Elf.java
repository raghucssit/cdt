/*******************************************************************************
 * Copyright (c) 2000, 2024 QNX Software Systems and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     QNX Software Systems - initial API and implementation
 *     Markus Schorn (Wind River Systems)
 *     John Dallaway - Support offset into archive file (#630)
 *******************************************************************************/
package org.eclipse.cdt.utils.elf;

import static org.eclipse.cdt.internal.core.ByteUtils.makeInt;
import static org.eclipse.cdt.internal.core.ByteUtils.makeLong;
import static org.eclipse.cdt.internal.core.ByteUtils.makeShort;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.IAddress;
import org.eclipse.cdt.core.IAddressFactory;
import org.eclipse.cdt.core.ISymbolReader;
import org.eclipse.cdt.utils.Addr32;
import org.eclipse.cdt.utils.Addr32Factory;
import org.eclipse.cdt.utils.Addr64;
import org.eclipse.cdt.utils.Addr64Factory;
import org.eclipse.cdt.utils.ERandomAccessFile;
import org.eclipse.cdt.utils.debug.dwarf.DwarfReader;

public class Elf implements AutoCloseable {
	public static final int ELF32_ADDR_SIZE = 4;
	public static final int ELF32_OFF_SIZE = 4;
	public static final int ELF64_ADDR_SIZE = 8;
	public static final int ELF64_OFF_SIZE = 8;

	protected ERandomAccessFile efile;

	protected ELFhdr ehdr;
	protected Section[] sections;
	protected String file;
	protected byte[] section_strtab;

	private Symbol[] symbols;
	/** .symtab section */
	private Symbol[] symbolsTable;
	/** .dynSym section */
	private Symbol[] dynamicSymbols;

	protected String EMPTY_STRING = ""; //$NON-NLS-1$
	private long elfOffset;

	public class ELFhdr {

		/* e_ident offsets */
		public static final int EI_MAG0 = 0;
		public static final int EI_MAG1 = 1;
		public static final int EI_MAG2 = 2;
		public static final int EI_MAG3 = 3;
		public static final int EI_CLASS = 4;
		public static final int EI_DATA = 5;
		public static final int EI_VERSION = 6;
		public static final int EI_PAD = 7;
		public static final int EI_NDENT = 16;

		/* e_ident[EI_CLASS] */
		public static final int ELFCLASSNONE = 0;
		public static final int ELFCLASS32 = 1;
		public static final int ELFCLASS64 = 2;

		/* e_ident[EI_DATA] */
		public static final int ELFDATANONE = 0;
		public static final int ELFDATA2LSB = 1;
		public static final int ELFDATA2MSB = 2;

		/* values of e_type */
		public static final int ET_NONE = 0;
		public static final int ET_REL = 1;
		public static final int ET_EXEC = 2;
		public static final int ET_DYN = 3;
		public static final int ET_CORE = 4;
		public static final int ET_LOPROC = 0xff00;
		public static final int ET_HIPROC = 0xffff;

		/* values of e_machine */
		public static final int EM_NONE = 0;
		public static final int EM_M32 = 1;
		public static final int EM_SPARC = 2;
		public static final int EM_386 = 3;
		public static final int EM_68K = 4;
		public static final int EM_88K = 5;
		public static final int EM_486 = 6;
		public static final int EM_860 = 7;
		public static final int EM_MIPS = 8;
		public static final int EM_MIPS_RS3_LE = 10;
		public static final int EM_RS6000 = 11;
		public static final int EM_PARISC = 15;
		public static final int EM_nCUBE = 16;
		public static final int EM_VPP550 = 17;
		public static final int EM_SPARC32PLUS = 18;
		public static final int EM_PPC = 20;
		public static final int EM_PPC64 = 21;
		/** @since 9.0*/
		public static final int EM_V800 = 36;
		public static final int EM_ARM = 40;
		public static final int EM_SH = 42;
		public static final int EM_SPARCV9 = 43;
		public static final int EM_TRICORE = 44;
		public static final int EM_H8_300 = 46;
		public static final int EM_H8_300H = 47;
		public static final int EM_IA_64 = 50;
		public static final int EM_COLDFIRE = 52;
		public static final int EM_STARCORE = 58;
		public static final int EM_X86_64 = 62;
		public static final int EM_ST100 = 60;

		/** @since 5.2 */
		public static final int EM_68HC08 = 71; /* Freescale MC68HC08 Microcontroller */

		public static final int EM_AVR = 83;
		public static final int EM_FR30 = 84; /* Fujitsu FR30 */
		public static final int EM_V850 = 87;
		public static final int EM_M32R = 88;
		public static final int EM_MN10300 = 89;
		public static final int EM_MN10200 = 90;
		public static final int EM_XTENSA = 94;
		public static final int EM_MSP430 = 105;
		public static final int EM_BLACKFIN = 106;
		public static final int EM_EXCESS = 111;
		/** @since 5.5 */
		public static final int EM_ESIRISC = 111;
		public static final int EM_NIOSII = 113;
		public static final int EM_C166 = 116;
		public static final int EM_M16C = 117;

		/** @since 5.2 */
		public static final int EM_RS08 = 132; /* Freescale RS08 embedded processor */

		public static final int EM_MMDSP = 160;

		/** @since 5.4 */
		public static final int EM_RX = 173; /* Renesas RX Microcontroller */

		/** @since 5.4 */
		public static final int EM_RL78 = 197; /* Renesas RL78 Microcontroller */
		/** @since 6.0 */
		public static final int EM_AARCH64 = 183;
		/** @since 9.0*/
		public static final int EM_AVR32 = 185;
		/** @since 9.0*/
		public static final int EM_MICROBLAZE = 189;

		/** @since 7.0 */
		public static final int EM_RISCV = 243;

		public static final int EM_NIOS = 0xFEBB;
		public static final int EM_CYGNUS_POWERPC = 0x9025;
		public static final int EM_CYGNUS_M32R = 0x9041;
		public static final int EM_CYGNUS_V850 = 0x9080;
		public static final int EM_CYGNUS_MN10200 = 0xdead;
		public static final int EM_CYGNUS_MN10300 = 0xbeef;
		public static final int EM_CYGNUS_FR30 = 0x3330;
		public static final int EM_XSTORMY16 = 0xad45;
		public static final int EM_CYGNUS_FRV = 0x5441;
		public static final int EM_IQ2000 = 0xFEBA;
		public static final int EM_XILINX_MICROBLAZE = 0xbaab;
		public static final int EM_SDMA = 0xcafe;
		public static final int EM_CRADLE = 0x4d55;

		public byte e_ident[] = new byte[EI_NDENT];
		public int e_type; /* file type (Elf32_Half) */
		public int e_machine; /* machine type (Elf32_Half) */
		public long e_version; /* version number (Elf32_Word) */
		public IAddress e_entry; /* entry point (Elf32_Addr) */
		public long e_phoff; /* Program hdr offset (Elf32_Off) */
		public long e_shoff; /* Section hdr offset (Elf32_Off) */
		public long e_flags; /* Processor flags (Elf32_Word) */
		public short e_ehsize; /* sizeof ehdr (Elf32_Half) */
		public short e_phentsize; /* Program header entry size (Elf32_Half) */
		public short e_phnum; /* Number of program headers (Elf32_Half) */
		public short e_shentsize; /* Section header entry size (Elf32_Half) */
		public short e_shnum; /* Number of section headers (Elf32_Half) */
		public short e_shstrndx; /* String table index (Elf32_Half) */

		protected ELFhdr() throws IOException {
			efile.seek(0);
			efile.readFully(e_ident);
			if (e_ident[ELFhdr.EI_MAG0] != 0x7f || e_ident[ELFhdr.EI_MAG1] != 'E' || e_ident[ELFhdr.EI_MAG2] != 'L'
					|| e_ident[ELFhdr.EI_MAG3] != 'F')
				throw new IOException(CCorePlugin.getResourceString("Util.exception.notELF")); //$NON-NLS-1$
			efile.setEndian(e_ident[ELFhdr.EI_DATA] == ELFhdr.ELFDATA2LSB);
			e_type = efile.readShortE();
			e_machine = efile.readShortE();
			e_version = efile.readIntE();
			switch (e_ident[ELFhdr.EI_CLASS]) {
			case ELFhdr.ELFCLASS32: {
				byte[] addrArray = new byte[ELF32_ADDR_SIZE];
				efile.readFullyE(addrArray);
				e_entry = new Addr32(addrArray);
				e_phoff = efile.readIntE();
				e_shoff = efile.readIntE();
			}
				break;
			case ELFhdr.ELFCLASS64: {
				byte[] addrArray = new byte[ELF64_ADDR_SIZE];
				efile.readFullyE(addrArray);
				e_entry = new Addr64(addrArray);
				e_phoff = readUnsignedLong(efile);
				e_shoff = readUnsignedLong(efile);
			}
				break;
			case ELFhdr.ELFCLASSNONE:
			default:
				throw new IOException("Unknown ELF class " + e_ident[ELFhdr.EI_CLASS]); //$NON-NLS-1$
			}
			e_flags = efile.readIntE();
			e_ehsize = efile.readShortE();
			e_phentsize = efile.readShortE();
			e_phnum = efile.readShortE();
			e_shentsize = efile.readShortE();
			e_shnum = efile.readShortE();
			e_shstrndx = efile.readShortE();
		}

		protected ELFhdr(byte[] bytes) throws IOException {
			if (bytes.length <= e_ident.length) {
				throw new EOFException(CCorePlugin.getResourceString("Util.exception.notELF")); //$NON-NLS-1$
			}
			System.arraycopy(bytes, 0, e_ident, 0, e_ident.length);
			if (e_ident[ELFhdr.EI_MAG0] != 0x7f || e_ident[ELFhdr.EI_MAG1] != 'E' || e_ident[ELFhdr.EI_MAG2] != 'L'
					|| e_ident[ELFhdr.EI_MAG3] != 'F')
				throw new IOException(CCorePlugin.getResourceString("Util.exception.notELF")); //$NON-NLS-1$
			boolean isle = (e_ident[ELFhdr.EI_DATA] == ELFhdr.ELFDATA2LSB);
			int offset = e_ident.length;
			e_type = makeShort(bytes, offset, isle);
			offset += 2;
			e_machine = makeShort(bytes, offset, isle);
			offset += 2;
			e_version = makeInt(bytes, offset, isle);
			offset += 4;
			switch (e_ident[ELFhdr.EI_CLASS]) {
			case ELFhdr.ELFCLASS32: {
				byte[] addrArray = new byte[ELF32_ADDR_SIZE];
				System.arraycopy(bytes, offset, addrArray, 0, ELF32_ADDR_SIZE);
				offset += ELF32_ADDR_SIZE;
				e_entry = new Addr32(addrArray);
				e_phoff = makeInt(bytes, offset, isle);
				offset += ELF32_OFF_SIZE;
				e_shoff = makeInt(bytes, offset, isle);
				offset += ELF32_OFF_SIZE;
			}
				break;
			case ELFhdr.ELFCLASS64: {
				byte[] addrArray = new byte[ELF64_ADDR_SIZE];
				System.arraycopy(bytes, offset, addrArray, 0, ELF64_ADDR_SIZE);
				offset += ELF64_ADDR_SIZE;
				e_entry = new Addr64(addrArray);
				e_phoff = makeUnsignedLong(bytes, offset, isle);
				offset += ELF64_OFF_SIZE;
				e_shoff = makeUnsignedLong(bytes, offset, isle);
				offset += ELF64_OFF_SIZE;
			}
				break;
			case ELFhdr.ELFCLASSNONE:
			default:
				throw new IOException("Unknown ELF class " + e_ident[ELFhdr.EI_CLASS]); //$NON-NLS-1$
			}
			e_flags = makeInt(bytes, offset, isle);
			offset += 4;
			e_ehsize = makeShort(bytes, offset, isle);
			offset += 2;
			e_phentsize = makeShort(bytes, offset, isle);
			offset += 2;
			e_phnum = makeShort(bytes, offset, isle);
			offset += 2;
			e_shentsize = makeShort(bytes, offset, isle);
			offset += 2;
			e_shnum = makeShort(bytes, offset, isle);
			offset += 2;
			e_shstrndx = makeShort(bytes, offset, isle);
			offset += 2;
		}
	}

	public class Section {

		/* sh_type */
		public static final int SHT_NULL = 0;
		public static final int SHT_PROGBITS = 1;
		public static final int SHT_SYMTAB = 2;
		public static final int SHT_STRTAB = 3;
		public static final int SHT_RELA = 4;
		public static final int SHT_HASH = 5;
		public static final int SHT_DYNAMIC = 6;
		public static final int SHT_NOTE = 7;
		public static final int SHT_NOBITS = 8;
		public static final int SHT_REL = 9;
		public static final int SHT_SHLIB = 10;
		public static final int SHT_DYNSYM = 11;

		public static final int SHT_LOPROC = 0x70000000;

		/* sh_flags */
		public static final int SHF_WRITE = 1;
		public static final int SHF_ALLOC = 2;
		public static final int SHF_EXECINTR = 4;
		/**
		 * @since 6.6
		 */
		public static final int SHF_COMPRESSED = 2048;

		/* note_types */
		/**
		 * @since 5.7
		 */
		public static final int NT_GNU_BUILD_ID = 3;

		public long sh_name;
		public long sh_type;
		public long sh_flags;
		public IAddress sh_addr;
		public long sh_offset;
		public long sh_size;
		public long sh_link;
		public long sh_info;
		public long sh_addralign;
		public long sh_entsize;

		private final long objOffset; // the offset of this binary object within the file (eg archive file)

		public Section() {
			this(0);
		}

		/** @since 8.4 */
		public Section(long objOffset) {
			this.objOffset = objOffset;
		}

		/**
		 * @since 5.1
		 */
		public ByteBuffer mapSectionData() throws IOException {
			makeSureNotCompressed();
			return efile.getChannel().map(MapMode.READ_ONLY, sh_offset + objOffset, sh_size).load().asReadOnlyBuffer();
		}

		public byte[] loadSectionData() throws IOException {
			makeSureNotCompressed();
			byte[] data = new byte[(int) sh_size];
			efile.seek(sh_offset);
			efile.read(data);
			return data;
		}

		private void makeSureNotCompressed() throws IOException {
			if ((sh_flags & SHF_COMPRESSED) != 0) {
				// No point in continuing, any seek() or map() will be wrong.
				throw new IOException("Compressed sections are unsupported (SHF_COMPRESSED): " + toString()); //$NON-NLS-1$
			}
		}

		@Override
		public String toString() {
			try {
				if (section_strtab == null) {
					final int shstrndx = ehdr.e_shstrndx & 0xffff; // unsigned short
					if (shstrndx > sections.length || shstrndx < 0)
						return EMPTY_STRING;
					sections[shstrndx].makeSureNotCompressed();
					int size = (int) sections[shstrndx].sh_size;
					if (size <= 0 || size > efile.length())
						return EMPTY_STRING;
					section_strtab = new byte[size];
					efile.seek(sections[shstrndx].sh_offset);
					efile.read(section_strtab);
				}
				int str_size = 0;
				if (sh_name > section_strtab.length) {
					return EMPTY_STRING;
				}
				while (section_strtab[(int) sh_name + str_size] != 0)
					str_size++;
				return new String(section_strtab, (int) sh_name, str_size);
			} catch (IOException e) {
				return EMPTY_STRING;
			}
		}
	}

	protected String string_from_elf_section(Elf.Section section, int index) throws IOException {
		if (index > section.sh_size) {
			return EMPTY_STRING;
		}

		section.makeSureNotCompressed();
		StringBuilder str = new StringBuilder();
		//Most string symbols will be less than 50 bytes in size
		byte[] tmp = new byte[50];
		efile.seek(section.sh_offset + index);
		while (true) {
			int len = efile.read(tmp);
			for (int i = 0; i < len; i++) {
				if (tmp[i] == 0) {
					len = 0;
					break;
				}
				str.append((char) tmp[i]);
			}
			if (len <= 0) {
				break;
			}
		}

		return str.toString();
	}

	public class Symbol implements Comparable<Object> {

		/* Symbol bindings */
		public static final int STB_LOCAL = 0;
		public static final int STB_GLOBAL = 1;
		public static final int STB_WEAK = 2;
		/* Symbol type */
		public static final int STT_NOTYPE = 0;
		public static final int STT_OBJECT = 1;
		public static final int STT_FUNC = 2;
		public static final int STT_SECTION = 3;
		public static final int STT_FILE = 4;
		/* Special Indexes */
		public static final int SHN_UNDEF = 0;
		public static final int SHN_LORESERVE = 0xffffff00;
		public static final int SHN_LOPROC = 0xffffff00;
		public static final int SHN_HIPROC = 0xffffff1f;
		public static final int SHN_LOOS = 0xffffff20;
		public static final int SHN_HIOS = 0xffffff3f;
		public static final int SHN_ABS = 0xfffffff1;
		public static final int SHN_COMMON = 0xfffffff2;
		public static final int SHN_XINDEX = 0xffffffff;
		public static final int SHN_HIRESERVE = 0xffffffff;

		/* NOTE: 64 bit and 32 bit ELF sections has different order */
		public long st_name;
		public IAddress st_value;
		public long st_size;
		public short st_info;
		public short st_other;
		public short st_shndx;

		private String name = null;

		private final Section sym_section;

		public Symbol(Section section) {
			sym_section = section;
		}

		public int st_type() {
			return st_info & 0xf;
		}

		public int st_bind() {
			return (st_info >> 4) & 0xf;
		}

		@Override
		public int compareTo(Object obj) {
			/*
			 * long thisVal = 0; long anotherVal = 0; if ( obj instanceof Symbol ) {
			 * Symbol sym = (Symbol)obj; thisVal = this.st_value; anotherVal =
			 * sym.st_value; } else if ( obj instanceof Long ) { Long val =
			 * (Long)obj; anotherVal = val.longValue(); thisVal = this.st_value; }
			 * return (thisVal <anotherVal ? -1 : (thisVal==anotherVal ? 0 :
			 * 1));
			 */
			return this.st_value.compareTo(((Symbol) obj).st_value);
		}

		@Override
		public String toString() {
			if (name == null) {
				try {
					Section sections[] = getSections();
					Section symstr = sections[(int) sym_section.sh_link];
					name = string_from_elf_section(symstr, (int) st_name);
				} catch (IOException e) {
					return EMPTY_STRING;
				}
			}
			return name;
		}

	}

	/**
	 * We have to implement a separate compararator since when we do the binary
	 * search down below we are using a Long and a Symbol object and the Long
	 * doesn't know how to compare against a Symbol so if we compare Symbol vs
	 * Long it is ok, but not if we do Long vs Symbol.
	 */

	class SymbolComparator implements Comparator<Object> {

		IAddress val1, val2;

		@Override
		public int compare(Object o1, Object o2) {

			if (o1 instanceof IAddress) {
				val1 = (IAddress) o1;
			} else if (o1 instanceof Symbol) {
				val1 = ((Symbol) o1).st_value;
			} else {
				return -1;
			}

			if (o2 instanceof IAddress) {
				val2 = (IAddress) o2;
			} else if (o2 instanceof Symbol) {
				val2 = ((Symbol) o2).st_value;
			} else {
				return -1;
			}
			return val1.compareTo(val2);
		}
	}

	public class PHdr {

		public static final int PT_NULL = 0;
		public static final int PT_LOAD = 1;
		public static final int PT_DYNAMIC = 2;
		public static final int PT_INTERP = 3;
		public static final int PT_NOTE = 4;
		public static final int PT_SHLIB = 5;
		public static final int PT_PHDR = 6;

		public static final int PF_X = 1;
		public static final int PF_W = 2;
		public static final int PF_R = 4;
		/* NOTE: 64 bit and 32 bit ELF have different order and size of elements */
		public long p_type;
		public long p_offset;
		public IAddress p_vaddr;
		public IAddress p_paddr;
		public long p_filesz;
		public long p_memsz;
		public long p_flags;
		public long p_align;
	}

	public PHdr[] getPHdrs() throws IOException {
		if (ehdr.e_phnum == 0) {
			return new PHdr[0];
		}
		efile.seek(ehdr.e_phoff);
		final int length = ehdr.e_phnum & 0xffff; // interpret as unsigned short
		PHdr phdrs[] = new PHdr[length];
		for (int i = 0; i < length; i++) {
			phdrs[i] = new PHdr();
			switch (ehdr.e_ident[ELFhdr.EI_CLASS]) {
			case ELFhdr.ELFCLASS32: {
				byte[] addrArray = new byte[ELF32_ADDR_SIZE];

				phdrs[i].p_type = efile.readIntE();
				phdrs[i].p_offset = efile.readIntE();
				efile.readFullyE(addrArray);
				phdrs[i].p_vaddr = new Addr32(addrArray);
				efile.readFullyE(addrArray);
				phdrs[i].p_paddr = new Addr32(addrArray);
				phdrs[i].p_filesz = efile.readIntE();
				phdrs[i].p_memsz = efile.readIntE();
				phdrs[i].p_flags = efile.readIntE();
				phdrs[i].p_align = efile.readIntE();
			}
				break;
			case ELFhdr.ELFCLASS64: {
				byte[] addrArray = new byte[ELF64_ADDR_SIZE];

				phdrs[i].p_type = efile.readIntE();
				phdrs[i].p_flags = efile.readIntE();
				phdrs[i].p_offset = readUnsignedLong(efile);
				efile.readFullyE(addrArray);
				phdrs[i].p_vaddr = new Addr64(addrArray);
				efile.readFullyE(addrArray);
				phdrs[i].p_paddr = new Addr64(addrArray);
				phdrs[i].p_filesz = readUnsignedLong(efile);
				phdrs[i].p_memsz = readUnsignedLong(efile);
				phdrs[i].p_align = readUnsignedLong(efile);
			}
				break;
			case ELFhdr.ELFCLASSNONE:
			default:
				throw new IOException("Unknown ELF class " + ehdr.e_ident[ELFhdr.EI_CLASS]); //$NON-NLS-1$
			}

		}
		return phdrs;
	}

	public class Dynamic {

		public static final int DYN_ENT_SIZE_32 = 8;
		public static final int DYN_ENT_SIZE_64 = 16;

		public static final int DT_NULL = 0;
		public static final int DT_NEEDED = 1;
		public static final int DT_PLTRELSZ = 2;
		public static final int DT_PLTGOT = 3;
		public static final int DT_HASH = 4;
		public static final int DT_STRTAB = 5;
		public static final int DT_SYMTAB = 6;
		public static final int DT_RELA = 7;
		public static final int DT_RELASZ = 8;
		public static final int DT_RELAENT = 9;
		public static final int DT_STRSZ = 10;
		public static final int DT_SYMENT = 11;
		public static final int DT_INIT = 12;
		public static final int DT_FINI = 13;
		public static final int DT_SONAME = 14;
		public static final int DT_RPATH = 15;
		public long d_tag;
		public long d_val;
		private final Section section;
		private String name;

		protected Dynamic(Section section) {
			this.section = section;
		}

		@Override
		public String toString() {
			if (name == null) {
				switch ((int) d_tag) {
				case DT_NEEDED:
				case DT_SONAME:
				case DT_RPATH:
					try {
						Section symstr = sections[(int) section.sh_link];
						name = string_from_elf_section(symstr, (int) d_val);
					} catch (IOException e) {
						name = EMPTY_STRING;
					}
					break;
				default:
					name = EMPTY_STRING;
				}
			}
			return name;
		}
	}

	public Dynamic[] getDynamicSections(Section section) throws IOException {
		if (section.sh_type != Section.SHT_DYNAMIC) {
			return new Dynamic[0];
		}
		section.makeSureNotCompressed();
		ArrayList<Dynamic> dynList = new ArrayList<>();
		efile.seek(section.sh_offset);
		int off = 0;
		// We must assume the section is a table ignoring the sh_entsize as it
		// is not
		// set for MIPS.
		while (off < section.sh_size) {
			Dynamic dynEnt = new Dynamic(section);
			switch (ehdr.e_ident[ELFhdr.EI_CLASS]) {
			case ELFhdr.ELFCLASS32: {
				dynEnt.d_tag = efile.readIntE();
				dynEnt.d_val = efile.readIntE();
				off += Dynamic.DYN_ENT_SIZE_32;
			}
				break;
			case ELFhdr.ELFCLASS64: {
				dynEnt.d_tag = efile.readLongE();
				dynEnt.d_val = efile.readLongE();
				off += Dynamic.DYN_ENT_SIZE_64;
			}
				break;
			case ELFhdr.ELFCLASSNONE:
			default:
				throw new IOException("Unknown ELF class " + ehdr.e_ident[ELFhdr.EI_CLASS]); //$NON-NLS-1$
			}

			if (dynEnt.d_tag != Dynamic.DT_NULL)
				dynList.add(dynEnt);
		}
		return dynList.toArray(new Dynamic[0]);
	}

	private void commonSetup(String file, long offset) throws IOException {
		try {
			efile = new ERandomAccessFile(file, "r"); //$NON-NLS-1$
			efile.setFileOffset(offset);
			ehdr = new ELFhdr();
			this.file = file;
		} finally {
			if (ehdr == null) {
				dispose();
			}
		}
	}

	//A hollow entry, to be used with caution in controlled situations
	protected Elf() {
	}

	public Elf(String file, long offset) throws IOException {
		commonSetup(file, offset);
		elfOffset = offset;
	}

	public Elf(String file) throws IOException {
		commonSetup(file, 0);
	}

	public ELFhdr getELFhdr() throws IOException {
		return ehdr;
	}

	public class Attribute {

		public static final int ELF_TYPE_EXE = 1;
		public static final int ELF_TYPE_SHLIB = 2;
		public static final int ELF_TYPE_OBJ = 3;
		public static final int ELF_TYPE_CORE = 4;

		public static final int DEBUG_TYPE_NONE = 0;
		public static final int DEBUG_TYPE_STABS = 1;
		public static final int DEBUG_TYPE_DWARF = 2;

		String cpu;
		int type;
		int debugType;
		boolean bDebug;
		boolean isle;
		IAddressFactory addressFactory;

		public String getCPU() {
			return cpu;
		}

		public int getType() {
			return type;
		}

		public boolean hasDebug() {
			return debugType != DEBUG_TYPE_NONE;
		}

		public int getDebugType() {
			return debugType;
		}

		public boolean isLittleEndian() {
			return isle;
		}

		public IAddressFactory getAddressFactory() {
			return addressFactory;
		}
	}

	public Attribute getAttributes() throws IOException {
		Attribute attrib = new Attribute();

		switch (ehdr.e_type) {
		case Elf.ELFhdr.ET_CORE:
			attrib.type = Attribute.ELF_TYPE_CORE;
			break;
		case Elf.ELFhdr.ET_EXEC:
			attrib.type = Attribute.ELF_TYPE_EXE;
			break;
		case Elf.ELFhdr.ET_REL:
			attrib.type = Attribute.ELF_TYPE_OBJ;
			break;
		case Elf.ELFhdr.ET_DYN:
			attrib.type = Attribute.ELF_TYPE_SHLIB;
			break;
		}

		switch (ehdr.e_machine & 0xFFFF) {
		case Elf.ELFhdr.EM_386:
		case Elf.ELFhdr.EM_486:
			attrib.cpu = "x86"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_68K:
			attrib.cpu = "m68k"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_PPC:
		case Elf.ELFhdr.EM_CYGNUS_POWERPC:
		case Elf.ELFhdr.EM_RS6000:
			attrib.cpu = "ppc"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_PPC64:
			attrib.cpu = "ppc64"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_SH:
			attrib.cpu = "sh"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_ARM:
			attrib.cpu = "arm"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_AARCH64:
			attrib.cpu = "aarch64"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_MIPS_RS3_LE:
		case Elf.ELFhdr.EM_MIPS:
			attrib.cpu = "mips"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_SPARC32PLUS:
		case Elf.ELFhdr.EM_SPARC:
		case Elf.ELFhdr.EM_SPARCV9:
			attrib.cpu = "sparc"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_H8_300:
		case Elf.ELFhdr.EM_H8_300H:
			attrib.cpu = "h8300"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_V800:
			attrib.cpu = "v800"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_V850:
		case Elf.ELFhdr.EM_CYGNUS_V850:
			attrib.cpu = "v850"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_MN10300:
		case Elf.ELFhdr.EM_CYGNUS_MN10300:
			attrib.cpu = "mn10300"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_MN10200:
		case Elf.ELFhdr.EM_CYGNUS_MN10200:
			attrib.cpu = "mn10200"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_M32R:
			attrib.cpu = "m32r"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_FR30:
		case Elf.ELFhdr.EM_CYGNUS_FR30:
			attrib.cpu = "fr30"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_XSTORMY16:
			attrib.cpu = "xstormy16"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_CYGNUS_FRV:
			attrib.cpu = "frv"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_IQ2000:
			attrib.cpu = "iq2000"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_ESIRISC:
			attrib.cpu = "esirisc"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_NIOSII:
			attrib.cpu = "alteranios2"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_NIOS:
			attrib.cpu = "alteranios"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_IA_64:
			attrib.cpu = "ia64"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_COLDFIRE:
			attrib.cpu = "coldfire"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_AVR:
			attrib.cpu = "avr"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_AVR32:
			attrib.cpu = "avr32"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_MSP430:
			attrib.cpu = "msp430"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_XTENSA:
			attrib.cpu = "xtensa"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_ST100:
			attrib.cpu = "st100"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_X86_64:
			attrib.cpu = "x86_64"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_MICROBLAZE:
		case Elf.ELFhdr.EM_XILINX_MICROBLAZE:
			attrib.cpu = "microblaze"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_C166:
			attrib.cpu = "c166"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_TRICORE:
			attrib.cpu = "TriCore"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_M16C:
			attrib.cpu = "M16C"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_STARCORE:
			attrib.cpu = "StarCore"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_BLACKFIN:
			attrib.cpu = "bfin"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_SDMA:
			attrib.cpu = "sdma"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_CRADLE:
			attrib.cpu = "cradle"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_MMDSP:
			attrib.cpu = "mmdsp"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_RX:
			attrib.cpu = "rx"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_RL78:
			attrib.cpu = "rl78"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_68HC08:
			attrib.cpu = "hc08"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_RS08:
			attrib.cpu = "rs08"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_RISCV:
			attrib.cpu = "riscv"; //$NON-NLS-1$
			break;
		case Elf.ELFhdr.EM_NONE:
		default:
			attrib.cpu = "none"; //$NON-NLS-1$
		}
		switch (ehdr.e_ident[Elf.ELFhdr.EI_DATA]) {
		case Elf.ELFhdr.ELFDATA2LSB:
			attrib.isle = true;
			break;
		case Elf.ELFhdr.ELFDATA2MSB:
			attrib.isle = false;
			break;
		}
		switch (ehdr.e_ident[ELFhdr.EI_CLASS]) {
		case ELFhdr.ELFCLASS32:
			attrib.addressFactory = new Addr32Factory();
			break;
		case ELFhdr.ELFCLASS64:
			attrib.addressFactory = new Addr64Factory();
			break;
		case ELFhdr.ELFCLASSNONE:
		default:
			attrib.addressFactory = null;
		}
		// getSections
		// find .debug using toString
		Section[] sec = getSections();
		if (sec != null) {
			for (int i = 0; i < sec.length; i++) {
				String s = sec[i].toString();
				if (s.startsWith(".debug")) { //$NON-NLS-1$
					attrib.debugType = Attribute.DEBUG_TYPE_DWARF;
					break;
				} else if (s.equals(".stab")) { //$NON-NLS-1$
					attrib.debugType = Attribute.DEBUG_TYPE_STABS;
					break;
				}
			}
		}
		return attrib;
	}

	public static Attribute getAttributes(String file) throws IOException {
		try (Elf elf = new Elf(file)) {
			Attribute attrib = elf.getAttributes();
			return attrib;
		}
	}

	public static Attribute getAttributes(byte[] array) throws IOException {
		try (Elf emptyElf = new Elf()) {
			emptyElf.ehdr = emptyElf.new ELFhdr(array);
			emptyElf.sections = new Elf.Section[0];
			Attribute attrib = emptyElf.getAttributes();

			return attrib;
		}
	}

	public static boolean isElfHeader(byte[] e_ident) {
		return e_ident != null && e_ident.length >= 4 && e_ident[ELFhdr.EI_MAG0] == 0x7f
				&& e_ident[ELFhdr.EI_MAG1] == 'E' && e_ident[ELFhdr.EI_MAG2] == 'L' && e_ident[ELFhdr.EI_MAG3] == 'F';
	}

	@Override
	public void close() {
		dispose();
	}

	public void dispose() {
		try {
			if (efile != null) {
				efile.close();
				efile = null;
			}
		} catch (IOException e) {
		}
	}

	/**
	 * Make sure we do not leak the fds.
	 */
	@Override
	protected void finalize() throws Throwable {
		try {
			dispose();
		} finally {
			super.finalize();
		}
	}

	public Section getSectionByName(String name) throws IOException {
		if (sections == null)
			getSections();
		for (int i = 0; i < sections.length; i++) {
			if (sections[i].toString().equals(name)) {
				return sections[i];
			}
		}
		return null;
	}

	public Section[] getSections(int type) throws IOException {
		if (sections == null)
			getSections();
		ArrayList<Section> slist = new ArrayList<>();
		for (int i = 0; i < sections.length; i++) {
			if (sections[i].sh_type == type)
				slist.add(sections[i]);
		}
		return slist.toArray(new Section[0]);
	}

	/**
	 * @throws ElfClassNoneException if {@link ELFhdr#ELFCLASSNONE} header is read
	 */
	public Section[] getSections() throws IOException {
		if (sections == null) {
			if (ehdr.e_shoff == 0) {
				sections = new Section[0];
				return sections;
			}
			final int length = ehdr.e_shnum & 0xffff; // unsigned short
			sections = new Section[length];
			for (int i = 0; i < length; i++) {
				efile.seek(ehdr.e_shoff + i * (ehdr.e_shentsize & 0xffff)); // unsigned short
				sections[i] = new Section(elfOffset);
				sections[i].sh_name = efile.readIntE();
				sections[i].sh_type = efile.readIntE();
				switch (ehdr.e_ident[ELFhdr.EI_CLASS]) {
				case ELFhdr.ELFCLASS32: {
					byte[] addrArray = new byte[ELF32_ADDR_SIZE];
					sections[i].sh_flags = efile.readIntE();
					efile.readFullyE(addrArray);
					sections[i].sh_addr = new Addr32(addrArray);
					sections[i].sh_offset = efile.readIntE();
					sections[i].sh_size = efile.readIntE();
				}
					break;
				case ELFhdr.ELFCLASS64: {
					byte[] addrArray = new byte[ELF64_ADDR_SIZE];
					sections[i].sh_flags = efile.readLongE();
					efile.readFullyE(addrArray);
					sections[i].sh_addr = new Addr64(addrArray);
					sections[i].sh_offset = readUnsignedLong(efile);
					sections[i].sh_size = readUnsignedLong(efile);
				}
					break;
				case ELFhdr.ELFCLASSNONE:
				default:
					throw new ElfClassNoneException("Unknown ELF class " + ehdr.e_ident[ELFhdr.EI_CLASS]); //$NON-NLS-1$
				}

				sections[i].sh_link = efile.readIntE();
				sections[i].sh_info = efile.readIntE();
				switch (ehdr.e_ident[ELFhdr.EI_CLASS]) {
				case ELFhdr.ELFCLASS32: {
					sections[i].sh_addralign = efile.readIntE();
					sections[i].sh_entsize = efile.readIntE();
				}
					break;
				case ELFhdr.ELFCLASS64: {
					sections[i].sh_addralign = efile.readLongE();
					sections[i].sh_entsize = readUnsignedLong(efile);
				}
					break;
				case ELFhdr.ELFCLASSNONE:
				default:
					throw new ElfClassNoneException("Unknown ELF class " + ehdr.e_ident[ELFhdr.EI_CLASS]); //$NON-NLS-1$
				}
			}
		}
		return sections;
	}

	/**
	 * Symbol iterator, iterates over an elf file. Note: the iterator must be closed at the end in order to avoid resource leaks.
	 *
	 * TODO: move to another file when @link {@Link Symbol} can be made static.
	 */
	private class ElfSectionIterator implements Iterator<Symbol>, Closeable {

		private final int nbSymbols;
		private final ERandomAccessFile innerEfile;
		private final Section section;
		private final byte arch;
		private int position = 0;

		public ElfSectionIterator(ERandomAccessFile eFile, byte b, Section sectionToRead, byte architecture)
				throws IOException {
			int numSyms = 1;
			section = sectionToRead;
			if (section.sh_entsize != 0) {
				numSyms = (int) section.sh_size / (int) section.sh_entsize;
			}
			section.makeSureNotCompressed();
			nbSymbols = numSyms;
			innerEfile = new ERandomAccessFile(eFile.getPath(), "r"); //$NON-NLS-1$
			innerEfile.setFileOffset(elfOffset);
			innerEfile.setEndian(efile.order() == ByteOrder.LITTLE_ENDIAN);
			arch = architecture;
		}

		@Override
		public boolean hasNext() {
			return position < nbSymbols;
		}

		@Override
		public Symbol next() {
			long innerOffset = section.sh_entsize * position + section.sh_offset;
			position++;
			try {
				innerEfile.seek(innerOffset);
				Symbol symbol = new Symbol(section);

				switch (arch) {
				case ELFhdr.ELFCLASS32: {
					byte[] addrArray = new byte[ELF32_ADDR_SIZE];

					symbol.st_name = innerEfile.readIntE();
					innerEfile.readFullyE(addrArray);
					symbol.st_value = new Addr32(addrArray);
					symbol.st_size = innerEfile.readIntE();
					symbol.st_info = innerEfile.readByte();
					symbol.st_other = innerEfile.readByte();
					symbol.st_shndx = innerEfile.readShortE();
					break;
				}
				case ELFhdr.ELFCLASS64: {
					byte[] addrArray = new byte[ELF64_ADDR_SIZE];

					symbol.st_name = innerEfile.readIntE();
					symbol.st_info = innerEfile.readByte();
					symbol.st_other = innerEfile.readByte();
					symbol.st_shndx = innerEfile.readShortE();
					innerEfile.readFullyE(addrArray);
					symbol.st_value = new Addr64(addrArray);
					symbol.st_size = innerEfile.readLongE();
					if (symbol.st_size < 0) {
						throw new NoSuchElementException("Maximal file offset is " + Long.toHexString(Long.MAX_VALUE) + //$NON-NLS-1$
								" given offset is " + Long.toHexString(symbol.st_size)); //$NON-NLS-1$
					}
					break;
				}
				case ELFhdr.ELFCLASSNONE:
				default:
					throw new NoSuchElementException("Unknown ELF class " + arch); //$NON-NLS-1$
				}
				return symbol;
			} catch (IOException e) {
				throw new NoSuchElementException(e.getMessage());
			}
		}

		@Override
		public void close() throws IOException {
			innerEfile.close();
		}

	}

	private Symbol[] loadSymbolsBySection(Section section) throws IOException {
		int numSyms = 1;
		if (section.sh_entsize != 0) {
			numSyms = (int) section.sh_size / (int) section.sh_entsize;
		}
		section.makeSureNotCompressed();
		ArrayList<Symbol> symList = new ArrayList<>(numSyms);
		try (ElfSectionIterator elfIterator = symbolIterator(section)) {
			while (elfIterator.hasNext()) {
				Symbol symbol = elfIterator.next();
				if (symbol.st_info == 0)
					continue;
				symList.add(symbol);
			}
		}
		Symbol[] results = symList.toArray(new Symbol[0]);
		Arrays.sort(results);
		return results;
	}

	/**
	 * Get a symbol iterator
	 * @param section the section to iterate over
	 * @return an iterator that returns symbols of a given section
	 * @throws IOException If the file is corrupt
	 * @since 7.0
	 */
	public ElfSectionIterator symbolIterator(Section section) throws IOException {
		return new ElfSectionIterator(efile, ehdr.e_ident[ELFhdr.EI_CLASS], section, ehdr.e_ident[ELFhdr.EI_CLASS]);
	}

	public void loadSymbols() throws IOException {
		Section symbolsTableSection = null;
		Section dynamicSymbolSection = null;
		if (symbols == null) {
			Section section[] = getSections(Section.SHT_SYMTAB);
			if (section.length > 0) {
				symbolsTableSection = section[0];
				symbolsTable = loadSymbolsBySection(section[0]);
			} else {
				symbolsTable = new Symbol[0];
			}

			section = getSections(Section.SHT_DYNSYM);
			if (section.length > 0) {
				dynamicSymbolSection = section[0];
				dynamicSymbols = loadSymbolsBySection(section[0]);
			} else {
				dynamicSymbols = new Symbol[0];
			}

			if (symbolsTableSection != null) {
				symbols = symbolsTable;
			} else if (dynamicSymbolSection != null) {
				symbols = dynamicSymbols;
			}
		}
	}

	public Symbol[] getSymbols() {
		return symbols;
	}

	public Symbol[] getDynamicSymbols() {
		return dynamicSymbols;
	}

	public Symbol[] getSymtabSymbols() {
		return symbolsTable;
	}

	/* return the address of the function that address is in */
	public Symbol getSymbol(IAddress vma) {
		if (symbols == null) {
			return null;
		}

		//@@@ If this works, move it to a single instance in this class.
		SymbolComparator symbol_comparator = new SymbolComparator();

		int ndx = Arrays.binarySearch(symbols, vma, symbol_comparator);
		if (ndx > 0)
			return symbols[ndx];
		if (ndx == -1) {
			return null;
		}
		ndx = -ndx - 1;
		return symbols[ndx - 1];
	}

	/*
	 * public long swapInt( long val ) { if ( ehdr.e_ident[ELFhdr.EI_DATA] ==
	 * ELFhdr.ELFDATA2LSB ) { short tmp[] = new short[4]; tmp[0] = (short)(val &
	 * 0x00ff); tmp[1] = (short)((val >> 8) & 0x00ff); tmp[2] = (short)((val >>
	 * 16) & 0x00ff); tmp[3] = (short)((val >> 24) & 0x00ff); return ((tmp[0] < <
	 * 24) + (tmp[1] < < 16) + (tmp[2] < < 8) + tmp[3]); } return val; }
	 *
	 * public int swapShort( short val ) { if ( ehdr.e_ident[ELFhdr.EI_DATA] ==
	 * ELFhdr.ELFDATA2LSB ) { short tmp[] = new short[2]; tmp[0] = (short)(val &
	 * 0x00ff); tmp[1] = (short)((val >> 8) & 0x00ff); return (short)((tmp[0] < <
	 * 8) + tmp[1]); } return val; }
	 */
	public String getFilename() {
		return file;
	}

	protected long readUnsignedLong(ERandomAccessFile file) throws IOException {
		long result = file.readLongE();
		if (result < 0) {
			throw new IOException("Maximal file offset is " + Long.toHexString(Long.MAX_VALUE) + //$NON-NLS-1$
					" given offset is " + Long.toHexString(result)); //$NON-NLS-1$
		}
		return result;
	}

	private ISymbolReader createDwarfReader() {
		DwarfReader reader = null;
		// Check if Dwarf data exists
		try {
			reader = new DwarfReader(this);
		} catch (IOException e) {
			// No Dwarf data in the Elf.
		}
		return reader;
	}

	/**
	 * Creates a new symbol reader instance on each call. Caller is responsible for closing
	 * the symbol reader
	 *
	 * @return symbol reader or {@code null} if couldn't create symbol reader
	 */
	public ISymbolReader getSymbolReader() {
		ISymbolReader reader = null;
		reader = createDwarfReader();
		return reader;
	}

	/** @since 5.4 */
	public static long makeUnsignedLong(byte[] val, int offset, boolean isle) throws IOException {
		long result = makeLong(val, offset, isle);
		if (result < 0) {
			throw new IOException("Maximal file offset is " + Long.toHexString(Long.MAX_VALUE) + //$NON-NLS-1$
					" given offset is " + Long.toHexString(result)); //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * Exception indicating that {@link ELFhdr#ELFCLASSNONE} header is read.
	 *
	 * @since 7.2
	 */
	public static class ElfClassNoneException extends IOException {

		ElfClassNoneException(String message) {
			super(message);
		}
	}
}

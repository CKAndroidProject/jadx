package jadx.gui.ui.dialog;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import jadx.api.JavaClass;
import jadx.api.JavaNode;
import jadx.gui.jobs.IndexService;
import jadx.gui.jobs.TaskStatus;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.MainWindow;
import jadx.gui.utils.CodeUsageInfo;
import jadx.gui.utils.NLS;
import jadx.gui.utils.UiUtils;

public class UsageDialog extends CommonSearchDialog {

	private static final long serialVersionUID = -5105405789969134105L;

	private final transient JNode node;

	public UsageDialog(MainWindow mainWindow, JNode node) {
		super(mainWindow);
		this.node = node;

		initUI();
		registerInitOnOpen();
		loadWindowPos();
	}

	@Override
	protected void openInit() {
		IndexService indexService = mainWindow.getCacheObject().getIndexService();
		if (indexService.isComplete()) {
			loadFinishedCommon();
			loadFinished();
			return;
		}
		List<JavaNode> useIn = node.getJavaNode().getUseIn();
		List<JavaClass> usageTopClsForIndex = useIn
				.stream()
				.map(JavaNode::getTopParentClass)
				.filter(indexService::isIndexNeeded)
				.distinct()
				.sorted(Comparator.comparing(JavaClass::getFullName))
				.collect(Collectors.toList());
		if (usageTopClsForIndex.isEmpty()) {
			loadFinishedCommon();
			loadFinished();
			return;
		}
		mainWindow.getBackgroundExecutor().execute(NLS.str("progress.load"),
				() -> {
					for (JavaClass cls : usageTopClsForIndex) {
						cls.decompile();
						indexService.indexCls(cls);
					}
				},
				(status) -> {
					if (status == TaskStatus.CANCEL_BY_MEMORY) {
						mainWindow.showHeapUsageBar();
						UiUtils.errorMessage(this, NLS.str("message.memoryLow"));
					}
					loadFinishedCommon();
					loadFinished();
				});
	}

	@Override
	protected void loadFinished() {
		resultsTable.setEnabled(true);
		performSearch();
	}

	@Override
	protected void loadStart() {
		resultsTable.setEnabled(false);
	}

	@Override
	protected synchronized void performSearch() {
		resultsModel.clear();

		CodeUsageInfo usageInfo = cache.getUsageInfo();
		if (usageInfo == null) {
			return;
		}
		resultsModel.addAll(usageInfo.getUsageList(node));
		// TODO: highlight only needed node usage
		highlightText = null;
		super.performSearch();
	}

	private void initUI() {
		JLabel lbl = new JLabel(NLS.str("usage_dialog.label"));
		JLabel nodeLabel = new JLabel(this.node.makeLongStringHtml(), this.node.getIcon(), SwingConstants.LEFT);
		lbl.setLabelFor(nodeLabel);

		JPanel searchPane = new JPanel();
		searchPane.setLayout(new FlowLayout(FlowLayout.LEFT));
		searchPane.add(lbl);
		searchPane.add(nodeLabel);
		searchPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		initCommon();
		JPanel resultsPanel = initResultsTable();
		JPanel buttonPane = initButtonsPanel();

		Container contentPane = getContentPane();
		contentPane.add(searchPane, BorderLayout.PAGE_START);
		contentPane.add(resultsPanel, BorderLayout.CENTER);
		contentPane.add(buttonPane, BorderLayout.PAGE_END);

		setTitle(NLS.str("usage_dialog.title"));
		pack();
		setSize(800, 500);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setModalityType(ModalityType.MODELESS);
	}
}
